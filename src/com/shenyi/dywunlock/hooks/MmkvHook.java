package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.HookUtil;
import com.shenyi.dywunlock.VipKeys;
import com.shenyi.dywunlock.XLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** MMKV 层（目标未集成则跳过） */
public final class MmkvHook {
    private MmkvHook() {}

    private static final String CLS = "com.tencent.mmkv.MMKV";

    public static void install(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists(CLS, cl);
        if (c == null) { XLog.i("未集成 MMKV，跳过"); return; }

        int n = 0;
        n += hook(c, "decodeBool", boolean.class, 0, false);
        n += hook(c, "decodeInt", int.class, 1, false);
        n += hook(c, "decodeLong", long.class, 2, false);
        n += hook(c, "decodeString", String.class, 4, false);
        n += hook(c, "encode", boolean.class, 0, true);
        n += hook(c, "encode", int.class, 1, true);
        n += hook(c, "encode", long.class, 2, true);
        n += hook(c, "encode", String.class, 4, true);
        XLog.i("MMKV 命中 " + n + "/8");
    }

    private static int hook(Class<?> c, String name, Class<?> type, final int kind, final boolean write) {
        boolean ok = HookUtil.hookMethod(c, name, new XC_MethodHook() {
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
                if (write) p.args[1] = v; else p.setResult(v);
                XLog.i("mmkv." + key + " -> " + v);
            }
        }, String.class, type);
        return ok ? 1 : 0;
    }
}
