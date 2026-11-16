package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.TimeNorm;
import com.shenyi.dywunlock.XLog;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 通用兜底网（对应 Deer God 的 METHOD_IS_VIP / METHOD_IS_SVIP 那套思路）。
 *
 * 只在"用户/账户/会员信息"这一类实体上动手：
 *   - 0 参 boolean 且名字命中 vip/member 的方法  -> true
 *   - 0 参 long 且名字命中 expire/end 的方法     -> CUTOFF_MS
 *   - 0 参 String 且名字命中 expire/end 的方法   -> CUTOFF_DATE
 *
 * 内容实体（CourseDetailInfoBean 等）名字里不含 user/account/memberinfo，
 * 不会被这里误判成"用户是会员"，它们的 isVip() 由 BeanHook 固定为 false。
 */
public final class GenericHook {
    private GenericHook() {}

    private static final Set<String> DONE = Collections.synchronizedSet(new HashSet<String>());

    private static final String[] USER_HINTS = {
            "user", "account", "memberinfo", "vipinfo", "login", "profile", "usermodel",
            "membermodel", "auth"
    };

    public static void install(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass",
                    String.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (p.hasThrowable()) return;
                            Object r = p.getResult();
                            if (!(r instanceof Class)) return;
                            scan((Class<?>) r);
                        }
                    });
            XLog.i("ClassLoader 兜底网已挂");
        } catch (Throwable t) {
            XLog.e("hook ClassLoader.loadClass 失败", t);
        }
    }

    private static boolean isTargetPackage(String n) {
        return n.startsWith("com.dyw.") || n.startsWith("com.dy.");
    }

    private static boolean looksLikeUserState(String simpleLower) {
        for (String h : USER_HINTS) if (simpleLower.contains(h)) return true;
        return false;
    }

    private static void scan(Class<?> c) {
        final String name = c.getName();
        if (!isTargetPackage(name)) return;
        final String simple = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.US);
        if (!looksLikeUserState(simple)) return;
        if (!DONE.add(name)) return;

        Method[] ms;
        try {
            ms = c.getDeclaredMethods();
        } catch (Throwable t) {
            return;
        }
        int hit = 0;
        for (final Method m : ms) {
            if (m.getParameterTypes().length != 0) continue;
            String mn = m.getName().toLowerCase(Locale.US);
            Class<?> rt = m.getReturnType();

            boolean stateName = mn.equals("isvip") || mn.equals("issvip") || mn.equals("ismember")
                    || mn.equals("isvipuser") || mn.equals("isvipmember") || mn.equals("hasvip")
                    || mn.equals("isopenvip") || mn.equals("isvipvalid") || mn.equals("isvipeffective");
            boolean timeName = mn.contains("expire") || mn.contains("endtime") || mn.contains("enddate")
                    || mn.contains("deadline") || mn.contains("vipend");

            try {
                if (rt == boolean.class && stateName) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) { p.setResult(Boolean.TRUE); }
                    });
                    hit++;
                } else if (rt == long.class && timeName) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            p.setResult(Long.valueOf(Const.CUTOFF_MS));
                        }
                    });
                    hit++;
                } else if (rt == String.class && timeName) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            p.setResult(Const.CUTOFF_DATE);
                        }
                    });
                    hit++;
                } else if (rt == int.class && stateName) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) { p.setResult(Integer.valueOf(1)); }
                    });
                    hit++;
                }
            } catch (Throwable t) {
                XLog.e("兜底 hook " + name + "#" + m.getName() + " 失败", t);
            }
        }
        if (hit > 0) {
            XLog.i("兜底网命中 " + name + " x" + hit);
        }
    }

    /** 供其他 hook 复用：把可能的时间值统一顶到无限期 */
    public static Object normTime(Object v, String key) {
        if (v instanceof Long) return Long.valueOf(TimeNorm.fixLong(((Long) v).longValue(), key));
        if (v instanceof Integer) return Integer.valueOf(TimeNorm.fixInt(((Integer) v).intValue(), key));
        if (v instanceof String) return TimeNorm.fixDateString((String) v);
        return v;
    }
}
