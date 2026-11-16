package com.shenyi.dywunlock.hooks;

import com.shenyi.dywunlock.Const;
import com.shenyi.dywunlock.HookUtil;
import com.shenyi.dywunlock.XLog;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 网络响应改写（docs/04 方案落地）。
 *
 * 全部用反射，不把 okhttp 打进模块（module 的 dex 里不该有 okhttp）：
 *   RealInterceptorChain#proceed(Request)  ->  Response
 *   Response.peekBody(long).string()       ->  body 文本
 *   ResponseBody.create(MediaType, String)
 *   Response.newBuilder().body(..).removeHeader(..).build()
 *
 * 命中会员字段就文本改写，然后回填新 body。
 */
public final class NetHook {
    private NetHook() {}

    private static final String CLS_CHAIN = "okhttp3.internal.http.RealInterceptorChain";
    private static final String CLS_REQ = "okhttp3.Request";
    private static final String CLS_BODY = "okhttp3.ResponseBody";
    private static final String CLS_MEDIA = "okhttp3.MediaType";

    public static void install(ClassLoader cl) {
        Class<?> chain = XposedHelpers.findClassIfExists(CLS_CHAIN, cl);
        Class<?> reqCls = XposedHelpers.findClassIfExists(CLS_REQ, cl);
        Class<?> bodyCls = XposedHelpers.findClassIfExists(CLS_BODY, cl);
        Class<?> mediaCls = XposedHelpers.findClassIfExists(CLS_MEDIA, cl);
        if (chain == null || reqCls == null || bodyCls == null || mediaCls == null) {
            XLog.i("okhttp 组件不全，跳过 NetHook");
            return;
        }

        Method m = HookUtil.get(chain, "proceed", reqCls);
        if (m == null) { XLog.i("proceed(Request) 不存在，跳过"); return; }

        final ClassLoader cl0 = cl;
        boolean ok = HookUtil.hook(m, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object resp = p.getResult();
                    Object req = p.args[0];
                    if (resp == null || req == null) return;

                    Object urlObj = XposedHelpers.callMethod(req, "url");
                    String url = String.valueOf(urlObj);
                    if (!url.contains("/appv2/") && !url.contains("/api/")) return;

                    Object peeked = XposedHelpers.callMethod(resp, "peekBody", Long.valueOf(1024L * 1024L));
                    if (peeked == null) return;
                    String body = (String) XposedHelpers.callMethod(peeked, "string");
                    if (body == null || body.length() == 0) return;

                    if (Const.DEBUG_NET) {
                        XLog.i("NET " + url + "\n    <- " + body.substring(0, Math.min(2400, body.length())));
                    }

                    String fixed = rewrite(body);
                    if (fixed == null) return;

                    Class<?> mediaCls = XposedHelpers.findClassIfExists(CLS_MEDIA, cl0);
                    Class<?> bodyCls = XposedHelpers.findClassIfExists(CLS_BODY, cl0);
                    if (mediaCls == null || bodyCls == null) return;

                    Object media = XposedHelpers.callStaticMethod(mediaCls, "parse", "application/json; charset=utf-8");
                    Method create = bodyCls.getMethod("create", mediaCls, String.class);
                    Object newBody = create.invoke(null, media, fixed);

                    Object builder = XposedHelpers.callMethod(resp, "newBuilder");
                    builder = XposedHelpers.callMethod(builder, "body", newBody);
                    builder = XposedHelpers.callMethod(builder, "removeHeader", "Content-Length");
                    builder = XposedHelpers.callMethod(builder, "removeHeader", "Content-Encoding");
                    Object newResp = XposedHelpers.callMethod(builder, "build");
                    p.setResult(newResp);
                    XLog.i("NET 改写响应 => " + url);
                } catch (Throwable t) {
                    XLog.e("NetHook 处理失败", t);
                }
            }
        });

        XLog.i("NetHook " + (ok ? "已挂 proceed(Request)" : "挂载失败"));
    }

    /** 文本级改写：只动会员相关字段 */
    private static String rewrite(String body) {
        if (body.length() > 512 * 1024) return null;
        String s = body;
        boolean changed = false;

        String[][] pairs = {
                {"\"isMember\":\"0\"", "\"isMember\":\"1\""},
                {"\"isMember\":0", "\"isMember\":1"},
                {"\"memberStatus\":\"0\"", "\"memberStatus\":\"1\""},
                {"\"memberStatus\":\"2\"", "\"memberStatus\":\"1\""},
                {"\"memberStatus\":0", "\"memberStatus\":1"},
                {"\"memberStatus\":2", "\"memberStatus\":1"},
                {"\"vipFlag\":0", "\"vipFlag\":1"},
                {"\"courseVipFlag\":1", "\"courseVipFlag\":0"},
        };
        for (String[] pair : pairs) {
            if (s.contains(pair[0])) {
                s = s.replace(pair[0], pair[1]);
                changed = true;
            }
        }

        Pattern pStr = Pattern.compile(
                "\"(memberEndDate|memberEndTime|vipEndDate|vipEndTime|expireDate|expireTime)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m1 = pStr.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m1.find()) {
            m1.appendReplacement(sb, "\"" + m1.group(1) + "\":\"" + Const.CUTOFF_DATE_CN + "\"");
            changed = true;
        }
        m1.appendTail(sb);
        s = sb.toString();

        Pattern pNum = Pattern.compile("\"(memberEndTime|vipEndTime|expireTime|endTime)\"\\s*:\\s*(\\d{10,13})");
        Matcher m2 = pNum.matcher(s);
        sb = new StringBuffer();
        while (m2.find()) {
            m2.appendReplacement(sb, "\"" + m2.group(1) + "\":" + Const.CUTOFF_MS);
            changed = true;
        }
        m2.appendTail(sb);
        s = sb.toString();

        // /appv2/home/getMemberList 的会员信息块（未登录时 status="4" = 未开通）
        Pattern pMember = Pattern.compile("\"memberInfo\"\\s*:\\s*\\{\"desc\":\"[^\"]*\",\"status\":\"[^\"]*\"\\}");
        Matcher m3 = pMember.matcher(s);
        sb = new StringBuffer();
        while (m3.find()) {
            m3.appendReplacement(sb, "\"memberInfo\":{\"desc\":\"已订阅\",\"status\":\"" + Const.VIP_STATUS + "\"}");
            changed = true;
        }
        m3.appendTail(sb);
        s = sb.toString();

        return changed ? s : null;
    }
}
