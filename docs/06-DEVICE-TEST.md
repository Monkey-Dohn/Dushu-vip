# 06 · 真机验证记录（OPPO R9s / LineageOS 21 / Magisk 30）

## 设备

```
型号      OPPO R9s (lineage_R9s)
系统      Android 14 (SDK 34), arm64-v8a
Root      Magisk 30.0，Zygisk 已启用
框架      Vector v2.2 (3080, JingMatrix 分支，API 102)，已装 zygisk_vector
目标      com.dyw 申怡读书（由 1.apk 安装，UID 10228）
模块      com.shenyi.dywunlock（本工程，UID 10208）
```

## 踩到的坑 1：装 LSPosed 导致 SIGSEGV

一开始装了官方 `LSPosed-v1.9.2-7024-zygisk`，结果**任何**被 hook 的 App 一启动就崩：

```
I LSPosed : Loading xposed for com.dyw/10228
F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
```

tombstone 只有 2 帧，停在 `__strlen_aarch64` ← `/memfd:jit-cache`，典型的框架自身与 ROM ART 不兼容。
把 Deer God（com.wengui.hook）作用域内的喜马拉雅拉起来同样崩 → **与我们的模块无关**。

根因：这台机器原本就跑 **Vector**（它沿用 `/data/adb/lspd` 作为数据目录），我又装了 LSPosed 1.9.2，
两套框架争用同一目录与 zygote 注入点。

处理：

```bash
su -c "rm -rf /data/adb/modules/zygisk_lsposed"     # 移除我装的 LSPosed
adb uninstall org.lsposed.manager
# 保留原来的 zygisk_vector，用它的 CLI 配置
su -c "/data/adb/lspd/cli status"                   # Framework Version 2.2 / API 102
su -c "/data/adb/lspd/cli modules enable com.shenyi.dywunlock"
su -c "/data/adb/lspd/cli scope add com.dyw/0"
```

配置也可以直接改 `modules_config.db`（Vector 未运行时）：

```sql
update modules set enabled=1 where module_pkg_name='com.shenyi.dywunlock';
insert or ignore into scope(mid, app_pkg_name, user_id) values (11,'com.dyw',0);
```

## 踩到的坑 2：`findAndHookMethod` 的 varargs 在 Vector 上失效

第一版全部报：

```
hook pref.getBoolean 失败 : XposedHelpers$ClassNotFoundError:
  parameter type must either be specified as Class or String
```

修法：不再传 `Class<?>[]`，改为显式取 `Method` 再 `XposedBridge.hookMethod`（新增 `HookUtil`）：

```java
Method m = c.getDeclaredMethod(name, sig);
m.setAccessible(true);
XposedBridge.hookMethod(m, new XC_MethodHook() { ... });
```

## 踩到的坑 3：会员到期日不走 JSON / SharedPreferences

界面上的「2028年10月22日 到期」既没有命中 `org.json.JSONObject`（只命中过一个无关的 `status`），
也没有命中 SharedPreferences。用 `uiautomator dump` 拿到控件 id 后回溯：

```
node text="2028年10月22日 到期" resource-id="com.dyw:id/tvVipTime"
  -> MineFragment.smali: tvVipTime.setText(userTokenResult.getMemberEndDate())
  -> smali/com/dy/common/model/user/UserInfo$UserTokenResult.smali
       isMember / memberStatus / memberEndDate / memberDays   （全是 String）
```

`MineFragment` 的状态机（反编译原文）：

```
memberStatus == "0"  -> tvDay="未订阅"
memberStatus == "2"  -> tvDay="已过期"
其它                  -> tvDay="已订阅" + tvVipTime.setText(getMemberEndDate())
```

于是新增 `UserInfoHook`，把 4 个 getter 钉死：

| getter | 返回 |
| --- | --- |
| `getMemberStatus()` | `"3"` |
| `getMemberEndDate()` | `"2099年12月31日 到期"` |
| `getMemberDays()` | `"26790"` |
| `getIsMember()` | `"1"` |

## 最终真机结果

`我的` 页（同一账号、同一台机器、仅重启 App）：

| | 装模块前 | 装模块后 |
| --- | --- | --- |
| 状态 | 已订阅 | 已订阅 |
| 到期 | **2028年10月22日 到期** | **2099年12月31日 到期** |
| 剩余天数 | 真实天数 | 26790 |

运行日志（`adb logcat | grep DywUnlock`，去重）：

```
==== 进入 com.dyw (com.dyw) ====
step: PrefHook ...      SharedPreferencesImpl 命中 5/5
                        EditorImpl 命中 5/5
step: JsonHook ...      JSONObject 命中 8/8
step: MmkvHook ...      未集成 MMKV，跳过
step: BeanHook ...      CourseDetailInfoBean.isVip() -> false
                        CourseDetailInfoBean.isVipCourse() -> false
                        CourseDetailBean.isVipCourse() -> false
                        CourseLessonInfoBean.isTryLesson() -> false
                        CourseLessonInfoBean.showTryLearn() -> false
                        MemberInfoBean.getStatus() -> 3
                        BeanHook 命中 9 项
step: UserInfoHook ...  UserTokenResult.getMemberStatus() -> "3"
                        UserTokenResult.getMemberEndDate() -> "2099年12月31日 到期"
                        UserTokenResult.getMemberDays() -> "26790"
                        UserTokenResult.getIsMember() -> "1"
                        UserTokenResult 命中 4/4
==== hook 安装完成，耗时 39ms，会员=永久 ====
```

运行期命中样例：

```
DBG json[endTime]=...        （调试开关打开时可见；正式版关闭）
json.endTime -> 无限期        （服务端响应里确实有 endTime 字段被改写）
```

## 复现步骤

```bash
adb install -r dist/DywVipUnlock-v1.0.0.apk
# Vector/LSPosed 里启用模块，作用域勾 com.dyw
adb shell am force-stop com.dyw
adb shell am start -n com.dyw/.activity.MainActivity
adb shell input tap 900 1840        # 底部「我的」
adb logcat | grep DywUnlock
```

## 仍然存在的边界

1. **服务端鉴权**：模块改的是客户端展示与本地判定；真正拉流时若服务端校验账号会员，仍需 `docs/04` 的响应改写。
2. **真实账号**：本次测试账号本身有到 2028 年的订阅，所以「解锁」证据来自到期时间被改成 2099，
   而不是「从无会员变成有会员」；全新账号首次登录时应表现为直接「已订阅」。
3. **H5 充值页**：`/h5/dyw/#/memberRecharge` 由 WebView 渲染，未在本次验证范围内。
