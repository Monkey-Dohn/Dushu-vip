# 03 · 安装与生效验证

## 前置

- 已 root 且装了 **LSPosed**（Zygisk 版或 Riru 版均可，xposedminversion=82）
- 目标机已安装 **申怡读书 com.dyw**（签名与原版一致即可）

## 安装

```bash
adb install -r dist/DywVipUnlock-v1.0.0.apk
```

或在手机上直接点开 APK 安装。

## 启用

1. 打开 **LSPosed 管理器 → 模块**，找到「申怡读书·永久会员」并启用
2. **作用域** 勾选：申怡读书（com.dyw）
3. 强制停止申怡读书：`adb shell am force-stop com.dyw`
4. 重新打开 App

## 验证（三条独立证据链）

### 1) LSPosed 日志

```bash
adb logcat -s LSPosed:Xposed 2>/dev/null | grep DywUnlock
```

典型输出：

```
[DywUnlock] ==== 进入 com.dyw (com.dyw) ====
[DywUnlock] SharedPreferencesImpl hooked
[DywUnlock] org.json.JSONObject hooked
[DywUnlock] 未集成 MMKV，跳过
[DywUnlock] com.dyw.bean.CourseDetailInfoBean.isVip() -> false
[DywUnlock] com.dyw.bean.CourseDetailInfoBean.isVipCourse() -> false
[DywUnlock] com.dyw.bean.CourseDetailBean.isVipCourse() -> false
[DywUnlock] MemberInfoBean.getStatus() -> 3
[DywUnlock] ClassLoader 兜底网已挂
[DywUnlock] ==== hook 安装完成，耗时 xxms，会员=永久 ====
```

运行时命中记录（点开会员书时出现）：

```
[DywUnlock] json.vipFlag -> 会员值
[DywUnlock] json.vipEndTime -> 无限期
[DywUnlock] pref.get isVip -> 会员值
[DywUnlock] 兜底网命中 com.dyw.xxx.UserInfoBean x2
```

### 2) 界面证据

- 我的 → 会员中心：应显示会员状态为已开通，到期时间为 **2099-12-31**
- 首页 → 读书会员：`status` 为会员中，不再弹"开通会员"
- 会员书播放：不再出现试听 / 购买弹窗，直接播放

### 3) 数据证据（root）

```bash
adb shell su -c 'ls /data/data/com.dyw/shared_prefs/'
adb shell su -c 'grep -r -i "vip\|member" /data/data/com.dyw/shared_prefs/ | head'
```

会员相关键的值应为会员值；到期类键应为 `4102444800000`（2100-01-01）或日期串 `2099-12-31 …`。

## 卸载 / 回滚

```bash
adb shell am force-stop com.dyw
adb uninstall com.shenyi.dywunlock
```

模块只做运行时 hook，不改动 com.dyw 的 APK 本体，卸载模块即恢复原状；
若曾写入会员键，清一次 App 数据（`adb shell pm clear com.dyw`）即彻底还原。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| LSPosed 里看不到模块 | 确认装的是本工程签名的 APK；重启一次 LSPosed |
| 日志有 `进入 com.dyw` 但无命中 | App 版本不同：换用兜底网（默认已启用），或在 `VipKeys` 里加键名 |
| 会员页显示开通但播放仍要买 | 服务端下发了带签名的播放地址，客户端 hook 到此为止；需要抓包改响应（见 docs/04） |
| 部分页面仍提示开会员 | H5 页面（`/h5/dyw/#/memberRecharge`）由 WebView 渲染，需 hook WebView 响应（见 docs/04） |
