package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.TimeNorm;
import com.shenyi.dywunlock.VipKeys;
import com.shenyi.dywunlock.XLog;

import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 网络层：服务端下发的 JSON 是会员状态的真正源头（/appv2/member/detail 等返回字符串再解析）。
 * 在 org.json.JSONObject 的取值点统一改写，比逐个 bean 打补丁覆盖面大。
 */
public final class JsonHook {
    private JsonHook() {}

    public static void install(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists("org.json.JSONObject", cl);
        if (c == null) { XLog.e("org.json.JSONObject 不存在", null); return; }

        hook(c, "getBoolean", boolean.class, 0);
        hook(c, "optBoolean", boolean.class, 0);
        hook(c, "getInt", int.class, 1);
        hook(c, "optInt", int.class, 1);
        hook(c, "getLong", long.class, 2);
        hook(c, "optLong", long.class, 2);
        hook(c, "getString", String.class, 4);
        hook(c, "optString", String.class, 4);
        XLog.i("org.json.JSONObject hooked");
    }

    private static void hook(Class<?> c, String name, final Class<?> ret, final int kind) {
        try {
            Class<?>[] sig;
            if (name.startsWith("opt")) {
                sig = new Class<?>[]{String.class, ret};
            } else {
                sig = new Class<?>[]{String.class};
            }
            XposedHelpers.findAndHookMethod(c, name, sig, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    String key = (String) p.args[0];
                    if (key == null) return;

                    if (VipKeys.isExpireKey(key)) {
                        switch (kind) {
                            case 2: p.setResult(Long.valueOf(Const.CUTOFF_MS)); break;
                            case 1: p.setResult(Integer.valueOf((int) Const.CUTOFF_SEC)); break;
                            case 4: p.setResult(Const.CUTOFF_DATE); break;
                            default: break;
                        }
                        XLog.i("json." + key + " -> 无限期");
                        return;
                    }
                    if (!VipKeys.isVipStateKey(key)) return;
                    switch (kind) {
                        case 0: p.setResult(Boolean.TRUE); break;
                        case 1: p.setResult(Integer.valueOf(1)); break;
                        case 2: p.setResult(Long.valueOf(Const.CUTOFF_MS)); break;
                        case 4: p.setResult(Const.VIP_STATUS); break;
                        default: break;
                    }
                    XLog.i("json." + key + " -> 会员值");
                }
            });
        } catch (Throwable t) {
            XLog.e("hook JSONObject." + name + " 失败", t);
        }
    }
}
