package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.HookUtil;
import com.shenyi.dywunlock.VipKeys;
import com.shenyi.dywunlock.XLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 网络层：服务端下发的 JSON 是会员状态的真正源头（/appv2/member/detail 等返回字符串再解析）。
 * 在 org.json.JSONObject 的取值点统一改写。
 */
public final class JsonHook {
    private JsonHook() {}

    public static void install(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists("org.json.JSONObject", cl);
        if (c == null) { XLog.e("org.json.JSONObject 不存在", null); return; }

        int n = 0;
        n += hook(c, "getBoolean", 0);
        n += hook(c, "getInt", 1);
        n += hook(c, "getLong", 2);
        n += hook(c, "getString", 4);
        n += hookOpt(c, "optBoolean", boolean.class, 0);
        n += hookOpt(c, "optInt", int.class, 1);
        n += hookOpt(c, "optLong", long.class, 2);
        n += hookOpt(c, "optString", String.class, 4);
        XLog.i("JSONObject 命中 " + n + "/8");
    }

    private static int hook(Class<?> c, String name, final int kind) {
        boolean ok = HookUtil.hookMethod(c, name, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                String key = (String) p.args[0];
                if (key == null) return;
                apply(p, key, kind, "json." + key);
            }
        }, String.class);
        return ok ? 1 : 0;
    }

    private static int hookOpt(Class<?> c, String name, Class<?> def, final int kind) {
        boolean ok = HookUtil.hookMethod(c, name, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                String key = (String) p.args[0];
                if (key == null) return;
                apply(p, key, kind, "json." + key);
            }
        }, String.class, def);
        return ok ? 1 : 0;
    }

    private static void apply(XC_MethodHook.MethodHookParam p, String key, int kind, String tag) {
        if (Const.DEBUG_JSON) {
            String k = key.toLowerCase(java.util.Locale.US);
            if (k.contains("vip") || k.contains("member") || k.contains("expire") || k.contains("end")
                    || k.contains("valid") || k.contains("level") || k.contains("status")
                    || k.contains("pay") || k.contains("deadline") || k.contains("date")) {
                Object raw = p.hasThrowable() ? ("<throw " + p.getThrowable() + ">") : p.getResult();
                XLog.i("DBG json[" + key + "]=" + raw);
            }
        }
        if (VipKeys.isExpireKey(key)) {
            switch (kind) {
                case 2: p.setResult(Long.valueOf(Const.CUTOFF_MS)); break;
                case 1: p.setResult(Integer.valueOf((int) Const.CUTOFF_SEC)); break;
                case 4: p.setResult(Const.CUTOFF_DATE); break;
                default: return;
            }
            XLog.i(tag + " -> 无限期");
            return;
        }
        if (!VipKeys.isVipStateKey(key)) return;
        switch (kind) {
            case 0: p.setResult(Boolean.TRUE); break;
            case 1: p.setResult(Integer.valueOf(1)); break;
            case 2: p.setResult(Long.valueOf(Const.CUTOFF_MS)); break;
            case 4: p.setResult(Const.VIP_STATUS); break;
            default: return;
        }
        XLog.i(tag + " -> 会员值");
    }
}
