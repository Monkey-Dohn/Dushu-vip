package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.VipKeys;
import com.shenyi.dywunlock.XLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 持久层：SharedPreferences 读写双向改写。
 *
 * 读：命中会员键 -> 直接返回会员值 / 无限期时间。
 * 写：命中会员键 -> 落盘前把值改写成会员值（防止 App 自己把 0 写回去覆盖我们的结果）。
 */
public final class PrefHook {
    private PrefHook() {}

    private static final String CLS_IMPL = "android.app.SharedPreferencesImpl";
    private static final String CLS_EDITOR = "android.app.SharedPreferencesImpl$EditorImpl";

    public static void install(ClassLoader cl) {
        hookImpl(cl);
        hookEditor(cl);
    }

    private static void hookImpl(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(CLS_IMPL, cl);
        if (c == null) { XLog.e("SharedPreferencesImpl 不存在", null); return; }

        hook(c, "getBoolean", new Class<?>[]{String.class, boolean.class}, 0);
        hook(c, "getInt", new Class<?>[]{String.class, int.class}, 1);
        hook(c, "getLong", new Class<?>[]{String.class, long.class}, 2);
        hook(c, "getFloat", new Class<?>[]{String.class, float.class}, 3);
        hook(c, "getString", new Class<?>[]{String.class, String.class}, 4);
        XLog.i("SharedPreferencesImpl hooked");
    }

    private static void hook(Class<?> c, String name, Class<?>[] sig, final int kind) {
        try {
            XposedHelpers.findAndHookMethod(c, name, concat(sig), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    String key = (String) p.args[0];
                    if (key == null) return;

                    if (VipKeys.isExpireKey(key)) {
                        switch (kind) {
                            case 1: p.setResult(Integer.valueOf((int) Const.CUTOFF_SEC)); return;
                            case 2: p.setResult(Long.valueOf(Const.CUTOFF_MS)); return;
                            case 4: p.setResult(Const.CUTOFF_DATE); return;
                            default: break;
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
                    XLog.i("pref.get " + key + " -> 会员值");
                }
            });
        } catch (Throwable t) {
            XLog.e("hook pref." + name + " 失败", t);
        }
    }

    private static void hookEditor(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(CLS_EDITOR, cl);
        if (c == null) return;
        hookPut(c, "putBoolean", boolean.class);
        hookPut(c, "putInt", int.class);
        hookPut(c, "putLong", long.class);
        hookPut(c, "putFloat", float.class);
        hookPut(c, "putString", String.class);
        XLog.i("SharedPreferences$EditorImpl hooked");
    }

    private static void hookPut(Class<?> c, String name, final Class<?> valueType) {
        try {
            XposedHelpers.findAndHookMethod(c, name, String.class, valueType, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    String key = (String) p.args[0];
                    if (key == null) return;

                    if (VipKeys.isExpireKey(key)) {
                        if (valueType == long.class) p.args[1] = Long.valueOf(Const.CUTOFF_MS);
                        else if (valueType == int.class) p.args[1] = Integer.valueOf((int) Const.CUTOFF_SEC);
                        else if (valueType == String.class) p.args[1] = Const.CUTOFF_DATE;
                        return;
                    }
                    if (!VipKeys.isVipStateKey(key)) return;
                    if (valueType == boolean.class) p.args[1] = Boolean.TRUE;
                    else if (valueType == int.class) p.args[1] = Integer.valueOf(1);
                    else if (valueType == long.class) p.args[1] = Long.valueOf(Const.CUTOFF_MS);
                    else if (valueType == float.class) p.args[1] = Float.valueOf(1f);
                    else if (valueType == String.class) p.args[1] = Const.VIP_STATUS;
                    XLog.i("pref.put " + key + " -> 会员值");
                }
            });
        } catch (Throwable t) {
            XLog.e("hook editor." + name + " 失败", t);
        }
    }

    private static Class<?>[] concat(Class<?>[] a) {
        return a;
    }
}
