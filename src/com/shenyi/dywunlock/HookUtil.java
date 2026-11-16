package com.shenyi.dywunlock;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * hook 工具：不用 XposedHelpers.findAndHookMethod 的 varargs 形式
 * （Vector/部分实现在接收 Class[] 时会当成单个参数，报 "parameter type must either be specified as Class or String"）。
 * 直接拿 Method 再 hookMethod，兼容性最好。
 */
public final class HookUtil {
    private HookUtil() {}

    public static Method get(Class<?> c, String name, Class<?>... sig) {
        try {
            Method m = c.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Method getStatic(Class<?> c, String name, Class<?>... sig) {
        return get(c, name, sig);
    }

    /** 命中则 hook 并返回 true */
    public static boolean hook(Method m, XC_MethodHook cb) {
        if (m == null) return false;
        try {
            XposedBridge.hookMethod(m, cb);
            return true;
        } catch (Throwable t) {
            XLog.e("hookMethod 失败 " + m, t);
            return false;
        }
    }

    public static boolean hookMethod(Class<?> c, String name, XC_MethodHook cb, Class<?>... sig) {
        return hook(get(c, name, sig), cb);
    }
}
