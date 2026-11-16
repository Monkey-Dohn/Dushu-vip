package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.HookUtil;
import com.shenyi.dywunlock.XLog;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 定点 hook：静态分析确认过的门禁方法（见 docs/01-ANALYSIS.md）。
 *
 * 1. CourseDetailInfoBean.isVip()/isVipCourse()      -> false
 * 2. CourseDetailBean.isVipCourse()                  -> false
 * 3. CourseLessonInfoBean.isTryLesson()/showTryLearn -> false（试听分片门禁）
 * 4. ReadVipBean$MemberInfoBean.getStatus()          -> "3"（会员中）
 * 5. ReadVipBean$MemberInfoBean.getDesc()            -> 永久会员
 * 另把 vipFlag / getVipFlag 归零，覆盖直接读字段的路径。
 */
public final class BeanHook {
    private BeanHook() {}

    public static void install(ClassLoader cl) {
        int n = 0;
        n += forceFalse(cl, "com.dyw.bean.CourseDetailInfoBean", "isVip");
        n += forceFalse(cl, "com.dyw.bean.CourseDetailInfoBean", "isVipCourse");
        n += forceFalse(cl, "com.dyw.bean.CourseDetailBean", "isVipCourse");
        n += forceFalse(cl, "com.dyw.bean.CourseLessonInfoBean", "isTryLesson");
        n += forceFalse(cl, "com.dyw.bean.CourseLessonInfoBean", "showTryLearn");
        n += zeroIntGetter(cl, "com.dyw.bean.CourseDetailInfoBean", "getVipFlag");
        n += zeroIntGetter(cl, "com.dyw.bean.CourseDetailInfoBean", "getCourseVipFlag");
        n += zeroIntGetter(cl, "com.dyw.bean.CourseDetailBean", "getVipFlag");
        n += zeroIntGetter(cl, "com.dyw.bean.CourseDetailBean", "getCourseVipFlag");
        n += memberStatus(cl);
        XLog.i("BeanHook 命中 " + n + " 项");
    }

    private static int forceFalse(ClassLoader cl, final String cls, final String method) {
        Class<?> c = XposedHelpers.findClassIfExists(cls, cl);
        if (c == null) { XLog.i("跳过(类不存在) " + cls); return 0; }
        Method m = HookUtil.get(c, method);
        if (m == null) { XLog.i("跳过(方法不存在) " + cls + "." + method); return 0; }
        boolean ok = HookUtil.hook(m, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                p.setResult(Boolean.FALSE);
            }
        });
        if (ok) XLog.i(cls + "." + method + "() -> false");
        return ok ? 1 : 0;
    }

    private static int zeroIntGetter(ClassLoader cl, String cls, String method) {
        Class<?> c = XposedHelpers.findClassIfExists(cls, cl);
        if (c == null) return 0;
        Method m = HookUtil.get(c, method);
        if (m == null) return 0;
        boolean ok = HookUtil.hook(m, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                if (p.getResult() instanceof Integer) p.setResult(Integer.valueOf(Const.FLAG_FREE));
            }
        });
        if (ok) XLog.i(cls + "." + method + "() -> 0");
        return ok ? 1 : 0;
    }

    private static int memberStatus(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists("com.dyw.bean.ReadVipBean$MemberInfoBean", cl);
        if (c == null) { XLog.i("跳过 ReadVipBean$MemberInfoBean"); return 0; }
        int n = 0;
        Method ms = HookUtil.get(c, "getStatus");
        if (ms != null && HookUtil.hook(ms, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                p.setResult(Const.VIP_STATUS);
            }
        })) {
            XLog.i("MemberInfoBean.getStatus() -> " + Const.VIP_STATUS);
            n++;
        }
        Method md = HookUtil.get(c, "getDesc");
        if (md != null && HookUtil.hook(md, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                Object r = p.getResult();
                if (r == null || ((String) r).length() == 0) p.setResult("永久会员 · 无限期");
            }
        })) {
            n++;
        }
        return n;
    }
}
