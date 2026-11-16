package com.shenyi.dywunlock;

/**
 * 全模块常量。命名沿用 Deer God 的思路：键名矩阵 + 时间截断值。
 */
public final class Const {
    private Const() {}

    /** 目标进程 */
    public static final String TARGET_PKG = "com.dyw";

    /** 会员到期时间统一顶到 2100-01-01 00:00:00（= 无限久） */
    public static final long CUTOFF_SEC = 4102444800L;      // 2100-01-01 00:00:00 UTC
    public static final long CUTOFF_MS  = 4102444800000L;   // 同上，毫秒
    public static final String CUTOFF_DATE = "2099-12-31 23:59:59";
    public static final String CUTOFF_DAY  = "2099-12-31";

    /** 会员状态"有效"的取值（读 ReadVipFragment.C2 反推：status == "3" 即会员中） */
    public static final String VIP_STATUS = "3";

    /** 内容级 vipFlag / courseVipFlag 的"免费"取值 */
    public static final int FLAG_FREE = 0;

    public static final String TAG = "DywUnlock";
}
