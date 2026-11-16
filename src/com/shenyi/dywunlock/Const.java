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
    /** 该 App 的到期文案由服务端直接下发，形如 "2028年10月22日 到期" */
    public static final String CUTOFF_DATE_CN = "2099年12月31日 到期";
    /** 剩余天数（服务端字符串），给一个足够大的值 */
    public static final String CUTOFF_DAYS = "26790";

    /** 会员状态"有效"的取值（读 ReadVipFragment.C2 反推：status == "3" 即会员中） */
    public static final String VIP_STATUS = "3";

    /** 内容级 vipFlag / courseVipFlag 的"免费"取值 */
    public static final int FLAG_FREE = 0;

    public static final String TAG = "DywUnlock";

    /**
     * 分层开关：ClassLoader 兜底网在 ART 上风险高（loadClass 期间做反射扫描可能递归/栈溢出），
     * 默认关闭；确认稳定后再打开。
     */
    public static final boolean ENABLE_GENERIC = false;
    public static final boolean ENABLE_PREF = true;
    public static final boolean ENABLE_JSON = true;
    public static final boolean ENABLE_MMKV = false;
    public static final boolean ENABLE_BEAN = true;
    public static final boolean ENABLE_USERINFO = true;

    /** 调试：把命中会员/时间关键词的 JSON 键值对打出来（真机定位字段用，发布前关掉） */
    public static final boolean DEBUG_JSON = false;
}
