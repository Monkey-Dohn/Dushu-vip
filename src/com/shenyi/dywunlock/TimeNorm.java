package com.shenyi.dywunlock;

/**
 * 时间截断器：任何"会员到期时间"取值都改写为无限久。
 *
 * 只对命中 VipKeys.isExpireKey() 的键生效，避免误伤正文发布时间之类的普通时间戳。
 */
public final class TimeNorm {
    private TimeNorm() {}

    /** 秒级时间戳合理区间：2001-09-09 ~ 2100-01-01 */
    private static final long SEC_MIN = 1_000_000_000L;
    private static final long SEC_MAX = 4_200_000_000L;
    /** 毫秒级时间戳合理区间 */
    private static final long MS_MIN = 1_000_000_000_000L;
    private static final long MS_MAX = 4_200_000_000_000L;

    public static long fixLong(long v, String key) {
        if (v <= 0L) return v;
        if (v >= SEC_MIN && v <= SEC_MAX) return Const.CUTOFF_SEC;
        if (v >= MS_MIN && v <= MS_MAX) return Const.CUTOFF_MS;
        return v;
    }

    public static int fixInt(int v, String key) {
        if (v <= 0) return v;
        if (v >= SEC_MIN && v <= SEC_MAX) return (int) Const.CUTOFF_SEC; // 极少见，兜底
        return v;
    }

    /** 字符串日期：形如 2024-05-01 / 2024-05-01 12:00:00 / 20240501 */
    public static String fixDateString(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() < 8 || t.length() > 32) return s;
        int digits = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') digits++;
            else if (c != '-' && c != '/' && c != ':' && c != ' ' && c != '.' && c != 'T' && c != 'Z') return s;
        }
        if (digits < 8) return s;
        // 形如 2024xx… 的日期才改写；"永久"/"长期" 之类原样保留
        if (!(t.startsWith("19") || t.startsWith("20") || t.startsWith("21"))) return s;
        return t.contains(":") ? Const.CUTOFF_DATE : Const.CUTOFF_DAY;
    }

    /** 会员状态/等级数值：任何 >0 的等级、1/2/3 状态都算有效会员 */
    public static Object fixStateValue(Object v, String key) {
        if (v instanceof Boolean) return Boolean.TRUE;
        if (v instanceof Integer) {
            int i = ((Integer) v).intValue();
            if (i == 0) return Integer.valueOf(1);
            return v;
        }
        if (v instanceof Long) {
            long l = ((Long) v).longValue();
            if (l == 0L) return Long.valueOf(1L);
            return v;
        }
        if (v instanceof String) {
            String s = (String) v;
            if (s.length() == 0 || "0".equals(s) || "false".equalsIgnoreCase(s)
                    || "null".equalsIgnoreCase(s) || "-1".equals(s)) {
                return Const.VIP_STATUS;
            }
            return v;
        }
        return v;
    }
}
