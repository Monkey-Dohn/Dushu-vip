# 04 · 还差一层：H5 与网络响应

`com.dyw` 的会员购买页是 H5（`/h5/dyw/#/memberRecharge?accesstoken=`），
会员详情接口 `/appv2/member/detail` 返回的是 **JSON 字符串**再由客户端解析。
模块已在 `org.json.JSONObject` 取值点改写过一次，如果某些页面仍显示未开通，说明它走的是
WebView 内的 JS 或 OkHttp 原始 body，按下面两条补上即可。

## 4.1 WebView 响应注入（H5 会员页）

```java
XposedHelpers.findAndHookMethod(
    "android.webkit.WebViewClient", cl, "shouldInterceptRequest",
    "android.webkit.WebView", "android.webkit.WebResourceRequest",
    new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) {
            Object resp = p.getResult();
            if (resp == null) return;
            // 只处理同源 API
            String url = ((android.webkit.WebResourceRequest) p.args[1]).getUrl().toString();
            if (!url.contains("/appv2/member/")) return;
            // 用反射读 body，字符串替换后重建 WebResourceResponse
            // WebResourceResponse(mime, encoding, new ByteArrayInputStream(fixed.getBytes("UTF-8")))
        }
    });
```

更省事的做法是 hook `WebView` 的 `loadUrl`/`evaluateJavascript`，在页面 ready 后注入：

```java
webView.evaluateJavascript(
  "(function(){"
+ "  var s=JSON.stringify;"
+ "  window.fetch=function(u,o){return s.call(window,u).then(function(r){"
+ "    if(String(u).indexOf('/appv2/member/')<0) return r;"
+ "    return r.clone().json().then(function(j){"
+ "      if(j.data){j.data.status='3';j.data.endTime=4102444800000;j.data.vipFlag=1;}"
+ "      return new Response(new Blob([JSON.stringify(j)]),{status:200});});});};"
+ "})();", null);
```

## 4.2 OkHttp 拦截（原始 body）

目标包内已确认存在 okhttp3（`work/deer/okhttp3` 之外，1.apk 亦带）。hook 其拦截链：

```java
Class<?> c = XposedHelpers.findClass("okhttp3.internal.http.RealInterceptorChain", cl);
XposedHelpers.findAndHookMethod(c, "proceed", "okhttp3.Request",
  new XC_MethodHook() {
    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
        Object resp = p.getResult();
        String url = ((okhttp3.Request) p.args[0]).url().toString();
        if (!url.contains("/appv2/member/")) return;
        // 取出 ResponseBody 字符串 -> 替换 vip 字段 -> 重建 Response
    }
  });
```

需要 `okhttp3.Response` 构造器：
`new Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200)
 .message("OK").body(ResponseBody.create(MediaType.parse("application/json"), fixed)).build()`

## 4.3 服务端播放地址（最后的边界）

如果播放地址由服务端在「会员校验」后才下发（`MainPresenter.z1` 带 courseType 参数那一条），
客户端 hook 能做的只到「以普通课程身份请求」；服务端若仍校验账号会员，就必须改响应：

- 抓包定位播放接口（`/appv2/course/...`）
- 把 `playUrl` / `audioUrl` 指向同课程免费资源的地址，或
- 直接劫持 `MediaPlayer.setDataSource(String)` 换成已缓存的本地文件：

```java
XposedHelpers.findAndHookMethod(android.media.MediaPlayer.class, "setDataSource",
    String.class, new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam p) {
            String u = (String) p.args[0];
            XLog.i("播放地址: " + u);
            // 需要时替换成本地缓存路径
        }
    });
```

这段默认不开，避免影响正常播放；调试时打开即可拿到真实播放地址。
