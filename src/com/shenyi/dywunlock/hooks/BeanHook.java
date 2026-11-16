package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.XLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 定点 hook：静态分析确认过的门禁方法与字段（见 docs/01-ANALYSIS.md）。
 *
 * 1. CourseDetailInfoBean.isVip()/isVipCourse()  -> false   （会员内容当免费内容放行）
 * 2. CourseDetailBean.isVipCourse()              -> false
 * 3. CourseLessonInfoBean 构造后 vipFlag/courseVipFlag -> 0
 * 4. ReadVipBean$MemberInfoBean.getStatus()      -> "3"     （会员中）
 * 5. ReadVipBean$MemberInfoBean.getDesc()        -> 永久会员
 */
public final class BeanHook {
    private BeanHook() {}

    public static void install(ClassLoader cl) {
        forceFalse(cl, "com.dyw.bean.CourseDetailInfoBean", "isVip");
        forceFalse(cl, "com.dyw.bean.CourseDetailInfoBean", "isVipCourse");
        forceFalse(cl, "com.dyw.bean.CourseDetailBean", "isVipCourse");
        clearFlagsOnCreate(cl, "com.dyw.bean.CourseLessonInfoBean", "vipFlag", "courseVipFlag");
        clearFlagsOnCreate(cl, "com.dyw.bean.CourseDetailInfoBean", "vipFlag", "courseVipFlag");
        clearFlagsOnCreate(cl, "com.dyw.bean.CourseDetailBean", "vipFlag", "courseVipFlag");
        memberStatus(cl);
    }

    private static void forceFalse(ClassLoader cl, final String cls, final String method) {
        Class<?> c = XposedHelpers.findClassIfExists(cls, cl);
        if (c == null) { XLog.i("跳过(不存在) " + cls); return; }
        try {
            XposedHelpers.findAndHookMethod(c, method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    p.setResult(Boolean.FALSE);
                }
            });
            XLog.i(cls + "." + method + "() -> false");
        } catch (Throwable t) {
            XLog.e("hook " + cls + "." + method + " 失败", t);
        }
    }

    private static void clearFlagsOnCreate(ClassLoader cl, final String cls, final String... fields) {
        Class<?> c = XposedHelpers.findClassIfExists(cls, cl);
        if (c == null) return;
        try {
            XposedHelpers.findAndHookMethod(c, "getVipFlag", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.getResult() instanceof Integer) p.setResult(Integer.valueOf(Const.FLAG_FREE));
                }
            });
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(c, "getCourseVipFlag", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.getResult() instanceof Integer) p.setResult(Integer.valueOf(Const.FLAG_FREE));
                }
            });
        } catch (Throwable ignored) {
        }
        for (final String f : fields) {
            try {
                XposedHelpers.findAndHookMethod(c, "setVipFlag", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if ("vipFlag".equals(f)) p.args[0] = Integer.valueOf(Const.FLAG_FREE);
                    }
                });
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(c, "setCourseVipFlag", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if ("courseVipFlag".equals(f)) p.args[0] = Integer.valueOf(Const.FLAG_FREE);
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private static void memberStatus(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists("com.dyw.bean.ReadVipBean$MemberInfoBean", cl);
        if (c == null) { XLog.i("跳过 ReadVipBean$MemberInfoBean"); return; }
        try {
            XposedHelpers.findAndHookMethod(c, "getStatus", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    p.setResult(Const.VIP_STATUS);
                }
            });
            XLog.i("MemberInfoBean.getStatus() -> " + Const.VIP_STATUS);
        } catch (Throwable t) {
            XLog.e("hook getStatus 失败", t);
        }
        try {
            XposedHelpers.findAndHookMethod(c, "getDesc", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    Object r = p.getResult();
                    if (r == null || ((String) r).length() == 0) p.setResult("永久会员 · 无限期");
                }
            });
        } catch (Throwable t) {
            XLog.e("hook getDesc 失败", t);
        }
    }
}
