package com.shenyi.dywunlock;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/** 日志：同时写 LSPosed 日志与 logcat（崩溃时 LSPosed 日志可能断在半路，logcat 更可靠） */
public final class XLog {
    private XLog() {}

    private static final boolean ON = true;
    private static final String P = "[" + Const.TAG + "] ";

    public static void i(String msg) {
        if (!ON) return;
        try { Log.i(Const.TAG, msg); } catch (Throwable ignored) {}
        try { XposedBridge.log(P + msg); } catch (Throwable ignored) {}
    }

    public static void e(String msg, Throwable t) {
        String s = msg + (t == null ? "" : " : " + t);
        try { Log.e(Const.TAG, s); } catch (Throwable ignored) {}
        try { XposedBridge.log(P + s); } catch (Throwable ignored) {}
    }
}
