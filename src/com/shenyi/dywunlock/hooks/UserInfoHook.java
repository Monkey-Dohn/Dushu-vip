package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.HookUtil;
import com.shenyi.dywunlock.XLog;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 用户信息实体：com.dy.common.model.user.UserInfo$UserTokenResult
 *
 * 静态分析（MineFragment，tvVipTime / tvDay / tvVipButton）：
 *   memberStatus "0" = 未订阅（显示"未订阅"）
 *   memberStatus "2" = 已过期（显示"已过期"）
 *   其它              = 已订阅（tvVipTime.setText(getMemberEndDate())，tvDay = "已订阅"）
 *
 * 所以：status -> "1"，endDate -> 2099-12-31 文案，days -> 大数值，isMember -> "1"
 */
public final class UserInfoHook {
    private UserInfoHook() {}

    private static final String CLS = "com.dy.common.model.user.UserInfo$UserTokenResult";

    public static void install(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(CLS, cl);
        if (c == null) { XLog.i("跳过(类不存在) " + CLS); return; }

        int n = 0;
        n += str(c, "getMemberStatus", Const.VIP_STATUS, "会员状态");
        n += str(c, "getMemberEndDate", Const.CUTOFF_DATE_CN, "到期时间");
        n += str(c, "getMemberDays", Const.CUTOFF_DAYS, "剩余天数");
        n += str(c, "getIsMember", "1", "是否会员");
        XLog.i("UserTokenResult 命中 " + n + "/4");
    }

    private static int str(Class<?> c, String name, final String value, String label) {
        Method m = HookUtil.get(c, name);
        if (m == null) return 0;
        boolean ok = HookUtil.hook(m, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                p.setResult(value);
            }
        });
        if (ok) XLog.i(c.getSimpleName() + "." + name + "() -> \"" + value + "\" (" + label + ")");
        return ok ? 1 : 0;
    }
}
