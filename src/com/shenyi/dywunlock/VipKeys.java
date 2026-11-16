package com.shenyi.dywunlock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 会员键名矩阵。
 *
 * 命中规则分三层：
 *   1) 精确键（来自 com.dyw 静态分析 + Deer God VIP_INT_KEYS 同族）
 *   2) 词根组合（vip/member/svip/vvvip + flag/status/level/type/expire/end/...）
 *   3) 到期时间键（单独处理：值要顶到 CUTOFF）
 */
public final class VipKeys {
    private VipKeys() {}

    /** 布尔/整型"是不是会员"键 */
    private static final Set<String> INT_EXACT = new HashSet<String>(Arrays.asList(
            "isvip", "isvipuser", "isvipmember", "issvip", "isvvip", "ismember",
            "vipflag", "vipstatus", "viptype", "viplevel", "vipopen", "vipvalid",
            "isvipflag", "isvipstatus", "memberflag", "memberstatus", "membertype",
            "memberlevel", "membervip", "memberopen", "svipflag", "svipstatus",
            "vip", "vipuser", "vipmember", "isopenvip", "vipisenable", "vipenabled",
            "isvipforever", "viplifetime", "vipforever"
    ));

    /** 字符串型"是不是/什么会员"键 */
    private static final Set<String> STR_EXACT = new HashSet<String>(Arrays.asList(
            "vipflag", "vipstatus", "vipname", "viptype", "viplevel", "memberflag",
            "memberstatus", "membername", "membertype", "memberlevel", "vipdesc",
            "memberdesc", "viptitle", "membertitle"
    ));

    /** 到期时间键（long 毫秒 / long 秒 / 字符串日期都可能是它的载体） */
    private static final Set<String> TIME_EXACT = new HashSet<String>(Arrays.asList(
            "vipexpire", "vipexpiretime", "vipexpiredate", "vipendtime", "vipenddate",
            "vipcutoff", "vipdeadline", "vipoverdue", "vipvalidtime", "vipvalidend",
            "memberexpire", "memberexpiretime", "memberendtime", "memberenddate",
            "memberdeadline", "membervalidtime", "svipexpire", "svipendtime",
            "expiretime", "expiredate", "endtime", "enddate", "deadline", "overduetime",
            "validtime", "validdate", "vip_expire_time", "vip_end_time"
    ));

    private static final String[] VIP_ROOTS = {"vip", "svip", "vvip", "member"};
    private static final String[] VIP_STATES = {
            "flag", "status", "level", "type", "open", "valid", "enable", "enabled",
            "grade", "role", "right", "auth", "state", "kind"
    };
    private static final String[] TIME_ROOTS = {"expire", "end", "deadline", "overdue", "valid"};

    private static String norm(String k) {
        if (k == null) return "";
        return k.toLowerCase(Locale.US).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static boolean hasAny(String k, String[] arr) {
        for (String a : arr) if (k.contains(a)) return true;
        return false;
    }

    /** 键是否表示会员开关 / 会员等级 */
    public static boolean isVipStateKey(String key) {
        String k = norm(key);
        if (k.isEmpty()) return false;
        if (INT_EXACT.contains(k) || STR_EXACT.contains(k)) return true;
        if (!hasAny(k, VIP_ROOTS)) return false;
        return hasAny(k, VIP_STATES);
    }

    /** 键是否表示会员到期时间 */
    public static boolean isExpireKey(String key) {
        String k = norm(key);
        if (k.isEmpty()) return false;
        if (TIME_EXACT.contains(k)) return true;
        if (!hasAny(k, VIP_ROOTS)) return false;
        return hasAny(k, TIME_ROOTS);
    }

    /** 键名像"会员到期时间"的展示文案键（如 vipTime、tvVipTime 之类资源 id 不在此列） */
    public static boolean isExpireLabel(String key) {
        String k = norm(key);
        return k.contains("viptime") || k.contains("membertime") || k.contains("expiretime");
    }
}
