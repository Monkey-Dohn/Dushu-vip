package com.shenyi.dywunlock;

import de.robv.android.xposed.XposedBridge;

/** 日志：全部打到 LSPosed 日志里，前缀统一，方便过滤 */
public final class XLog {
    private XLog() {}

    private static final boolean ON = true;
    private static final String P = "[" + Const.TAG + "] ";

    public static void i(String msg) {
        if (ON) XposedBridge.log(P + msg);
    }

    public static void e(String msg, Throwable t) {
        XposedBridge.log(P + msg + (t == null ? "" : " : " + t));
    }
}
