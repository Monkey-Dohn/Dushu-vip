# 07 · 服务端响应改写（NetHook）

## 挂载点

目标包内 okhttp **3.12.13**（`smali_classes4/okhttp3`）：

```
okhttp3.internal.http.RealInterceptorChain#proceed(okhttp3.Request) -> okhttp3.Response
```

在 `afterHookedMethod` 里：

1. `Request.url()` 过滤 `/appv2/`、`/api/`
2. `Response.peekBody(1MB).string()` 取 body 文本
3. 文本规则改写
4. `ResponseBody.create(MediaType, String)` + `Response.newBuilder().body(..).removeHeader("Content-Length").removeHeader("Content-Encoding").build()` 回填

全部用反射实现（模块 dex 不引入 okhttp）。

## 改写规则

| 目标 | 规则 |
| --- | --- |
| `"isMember":"0"` / `0` | → `"1"` / `1` |
| `"memberStatus":"0" / "2" / "4"` | → `"1"` |
| `"memberEndDate" / "memberEndTime" / "vipEndDate" / "vipEndTime" / "expireDate" / "expireTime"` | → `"2099年12月31日 到期"` |
| `"memberEndTime" / "vipEndTime" / "expireTime" / "endTime":<10~13位数字>` | → `4102444800000` |
| `"memberInfo":{"desc":"…","status":"…"}` | → `{"desc":"已订阅","status":"3"}` |
| `"vipFlag":0` | → `1` |
| `"courseVipFlag":1` | → `0`（会员课当免费课） |

## 真机实测（未登录，`pm clear com.dyw` 后）

服务端原始响应：

```json
{"code":200,"data":{"memberInfo":{"desc":"阅读是快乐的～","status":"4"}, ...}}
```

NET 改写后：

```json
{"code":200,"data":{"memberInfo":{"desc":"已订阅","status":"3"}, ...}}
```

日志：

```
NET https://app.shenyiedu.com/appv2/home/getList
    <- {"code":200,"data":{"memberInfo":{"desc":"阅读是快乐的～","status":"4"} …
NET 改写响应 => https://app.shenyiedu.com/appv2/home/getList
    <- {"code":200,"data":{"memberInfo":{"desc":"已订阅","status":"3"} …
```

界面：读书会员页头部从「阅读是快乐的~ / 立即开通」变成「已订阅 / 立即续费」。

## 抓到的接口清单（未登录浏览时）

```
/appv2/config/config                    /appv2/home/appHome
/appv2/home/getHomeAd                   /appv2/home/getHomeBanner
/appv2/home/getList                     /appv2/home/getMemberList
/appv2/home/getDailyReading             /appv2/home/homeActivity
/appv2/home/homeRecommend               /appv2/home/recommendCourse
/appv2/shop/point/issignin              /appv2/course/getCourseDetail
/appv2/course/modules                   /appv2/course/getLessonsAppendix
/appv2/courseNew/getCourseListNew       /appv2/courseNew/checkCoursePairedReadContent
/appv2/video/freeVideoList
```

## 关键发现：播放在服务端就被掐了

`/appv2/course/getCourseDetail` 里每节课的 **`fileUrl` 是空字符串**：

```json
{"buyFlag":0,"courseVipFlag":1,"fileUrl":"","lessonsNo":"2121133514505109505",
 "lessonsType":3,"name":"第5讲 我的魔钥","vipFlag":0,"videoTime":"710"}
```

也就是说：**未登录/非会员时，服务端不下发音频地址**。客户端拿到的是一串空 URL，ExoPlayer 打开失败，
App 弹出「播放错误码：2005 请重试或者联系客服处理！」。

所以 `NetHook` 能改的东西到此为止：

- 会员状态、到期时间、会员文案 ✅
- 会员课标记（`courseVipFlag`）✅
- **音频地址 ❌** —— 响应里根本没有可改的内容

要真正播放，只有三条路：

1. **登录一个会员账号**（服务端按 token 给 `fileUrl`，客户端 hook 只负责把状态/时间改成永久）；
2. 抓到会员态下 `fileUrl` 的规律（CDN 直链是否可由 `lessonsNo` 推导），在客户端替换空 URL；
3. 用真实会员账号做一次请求转发（自建代理，把未登录请求带上会员 token 重放）—— 属于服务端代理方案，不在模块范围内。
