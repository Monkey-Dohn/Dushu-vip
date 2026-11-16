package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.HookUtil;
import com.shenyi.dywunlock.VipKeys;
import com.shenyi.dywunlock.XLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 持久层：SharedPreferences 读写双向改写。
 * 读：命中会员键 -> 直接返回会员值 / 无限期时间。
 * 写：命中会员键 -> 落盘前改写成会员值。
 */
public final class PrefHook {
    private PrefHook() {}

    private static final String CLS_IMPL = "android.app.SharedPreferencesImpl";
    private static final String CLS_EDITOR = "android.app.SharedPreferencesImpl$EditorImpl";

    public static void install(ClassLoader cl) {
        Class<?> impl = XposedHelpers.findClassIfExists(CLS_IMPL, cl);
        if (impl == null) { XLog.e("SharedPreferencesImpl 不存在", null); return; }

        int n = 0;
        n += hookGet(impl, "getBoolean", boolean.class, 0);
        n += hookGet(impl, "getInt", int.class, 1);
        n += hookGet(impl, "getLong", long.class, 2);
        n += hookGet(impl, "getFloat", float.class, 3);
        n += hookGet(impl, "getString", String.class, 4);
        XLog.i("SharedPreferencesImpl 命中 " + n + "/5");

        Class<?> ed = XposedHelpers.findClassIfExists(CLS_EDITOR, cl);
        if (ed == null) return;
        int m = 0;
        m += hookPut(ed, "putBoolean", boolean.class, 0);
        m += hookPut(ed, "putInt", int.class, 1);
        m += hookPut(ed, "putLong", long.class, 2);
        m += hookPut(ed, "putFloat", float.class, 3);
        m += hookPut(ed, "putString", String.class, 4);
        XLog.i("EditorImpl 命中 " + m + "/5");
    }

    private static int hookGet(Class<?> c, String name, Class<?> type, final int kind) {
        final String keyName = "pref." + name;
        boolean ok = HookUtil.hookMethod(c, name, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                String key = (String) p.args[0];
                if (key == null) return;
                if (VipKeys.isExpireKey(key)) {
                    switch (kind) {
                        case 1: p.setResult(Integer.valueOf((int) Const.CUTOFF_SEC)); return;
                        case 2: p.setResult(Long.valueOf(Const.CUTOFF_MS)); return;
                        case 4: p.setResult(Const.CUTOFF_DATE); return;
                        default: return;
                    }
                }
                if (!VipKeys.isVipStateKey(key)) return;
                switch (kind) {
                    case 0: p.setResult(Boolean.TRUE); break;
                    case 1: p.setResult(Integer.valueOf(1)); break;
                    case 2: p.setResult(Long.valueOf(Const.CUTOFF_MS)); break;
                    case 3: p.setResult(Float.valueOf(1f)); break;
                    case 4: p.setResult(Const.VIP_STATUS); break;
                    default: break;
                }
                XLog.i(keyName + " " + key + " -> 会员值");
            }
        }, String.class, type);
        if (!ok) XLog.e("hook " + name + " 失败", null);
        return ok ? 1 : 0;
    }

    private static int hookPut(Class<?> c, String name, Class<?> type, final int kind) {
        final String keyName = "pref." + name;
        boolean ok = HookUtil.hookMethod(c, name, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                String key = (String) p.args[0];
                if (key == null) return;
                if (VipKeys.isExpireKey(key)) {
                    if (kind == 2) p.args[1] = Long.valueOf(Const.CUTOFF_MS);
                    else if (kind == 1) p.args[1] = Integer.valueOf((int) Const.CUTOFF_SEC);
                    else if (kind == 4) p.args[1] = Const.CUTOFF_DATE;
                    XLog.i(keyName + " " + key + " -> 无限期");
                    return;
                }
                if (!VipKeys.isVipStateKey(key)) return;
                if (kind == 0) p.args[1] = Boolean.TRUE;
                else if (kind == 1) p.args[1] = Integer.valueOf(1);
                else if (kind == 2) p.args[1] = Long.valueOf(Const.CUTOFF_MS);
                else if (kind == 3) p.args[1] = Float.valueOf(1f);
                else if (kind == 4) p.args[1] = Const.VIP_STATUS;
                XLog.i(keyName + " " + key + " -> 会员值");
            }
        }, String.class, type);
        return ok ? 1 : 0;
    }
}
