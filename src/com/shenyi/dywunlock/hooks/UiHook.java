package com.shenyi.dywunlock.hooks;

import android.view.View;
import android.widget.TextView;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.HookUtil;
import com.shenyi.dywunlock.XLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 未登录场景的 UI 兜底。
 *
 * MineFragment 有两条会员卡分支：
 *   已登录（UserTokenResult 非空）-> tvDay="已订阅" + tvVipTime=getMemberEndDate()
 *   未登录（cond_6 分支）        -> tvDay="阅读是快乐的~" + tvVipButton="立即开通" + tvVipTime 隐藏
 *
 * 未登录时 UserTokenResult 是 null，UserInfoHook 不会被调用，所以这里在 TextView.setText 层
 * 按文案特征改写，并把同布局里的 tvVipTime 显示出来、填 2099 到期文案。
 */
public final class UiHook {
    private UiHook() {}

    private static final String OFF_SUB = "阅读是快乐的~";
    private static final String BTN_OPEN = "立即开通";
    private static final String BTN_RENEW = "立即续费";
    private static final String SUBSCRIBED = "已订阅";

    public static void install(ClassLoader cl) {
        Class<?> tv = XposedHelpers.findClassIfExists("android.widget.TextView", cl);
        if (tv == null) { XLog.e("TextView 不存在", null); return; }
        boolean ok = HookUtil.hookMethod(tv, "setText", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (!(p.args[0] instanceof CharSequence)) return;
                    String s = p.args[0].toString();
                    if (s.length() < 3 || s.length() > 16) return;

                    if (s.contains("阅读是快乐")) {
                        p.args[0] = SUBSCRIBED;
                        fillExpire((TextView) p.thisObject);
                        XLog.i("UI: 未登录会员卡 -> 已订阅");
                    } else if (s.contains("立即开通")) {
                        p.args[0] = BTN_RENEW;
                        fillExpire((TextView) p.thisObject);
                        XLog.i("UI: 立即开通 -> 立即续费");
                    }
                } catch (Throwable ignored) {
                }
            }
        }, CharSequence.class);
        XLog.i("UiHook " + (ok ? "已挂 TextView.setText" : "挂载失败"));
    }

    /** 找到同一个布局里的 tvVipTime，显示并写入 2099 到期文案 */
    private static void fillExpire(TextView anchor) {
        try {
            View root = anchor.getRootView();
            if (root == null) return;
            int id = anchor.getResources().getIdentifier("tvVipTime", "id", Const.TARGET_PKG);
            if (id == 0) return;
            View v = root.findViewById(id);
            if (!(v instanceof TextView)) return;
            TextView t = (TextView) v;
            if (t.getVisibility() != View.VISIBLE) t.setVisibility(View.VISIBLE);
            String cur = t.getText() == null ? "" : t.getText().toString();
            if (!cur.contains("2099")) t.setText(Const.CUTOFF_DATE_CN);
        } catch (Throwable ignored) {
        }
    }
}
