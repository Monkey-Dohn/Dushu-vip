package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.VipKeys;
import com.shenyi.dywunlock.XLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * MMKV 层（腾讯 MMKV，很多 App 用它替代 SharedPreferences）。
 * 目标包若未集成 MMKV，这里静默跳过。
 */
public final class MmkvHook {
    private MmkvHook() {}

    private static final String CLS = "com.tencent.mmkv.MMKV";

    public static void install(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(CLS, cl);
        if (c == null) { XLog.i("未集成 MMKV，跳过"); return; }

        hook(c, "decodeBool", boolean.class, 0, false);
        hook(c, "decodeInt", int.class, 1, false);
        hook(c, "decodeLong", long.class, 2, false);
        hook(c, "decodeString", String.class, 4, false);

        hook(c, "encode", boolean.class, 0, true);
        hook(c, "encode", int.class, 1, true);
        hook(c, "encode", long.class, 2, true);
        hook(c, "encode", String.class, 4, true);
        XLog.i("MMKV hooked");
    }

    private static void hook(Class<?> c, String name, final Class<?> type, final int kind, final boolean write) {
        try {
            XposedHelpers.findAndHookMethod(c, name, String.class, type, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    String key = (String) p.args[0];
                    if (key == null) return;
                    boolean expire = VipKeys.isExpireKey(key);
                    if (!expire && !VipKeys.isVipStateKey(key)) return;

                    Object v;
                    if (expire) {
                        if (kind == 1) v = Integer.valueOf((int) Const.CUTOFF_SEC);
                        else if (kind == 2) v = Long.valueOf(Const.CUTOFF_MS);
                        else if (kind == 4) v = Const.CUTOFF_DATE;
                        else return;
                    } else {
                        if (kind == 0) v = Boolean.TRUE;
                        else if (kind == 1) v = Integer.valueOf(1);
                        else if (kind == 2) v = Long.valueOf(Const.CUTOFF_MS);
                        else v = Const.VIP_STATUS;
                    }
                    if (write) p.args[1] = v;
                    else p.setResult(v);
                    XLog.i("mmkv." + (write ? "encode " : "decode ") + key + " -> " + v);
                }
            });
        } catch (Throwable t) {
            XLog.e("hook MMKV." + name + " 失败", t);
        }
    }
}
