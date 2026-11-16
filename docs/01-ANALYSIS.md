# 01 · 目标侦察记录（申怡读书 com.dyw）

## 样本

| 项 | 值 |
| --- | --- |
| 目标 APK | `1.apk`（34,832,000 B） |
| 包名 | `com.dyw` |
| 应用名 | 申怡读书 |
| compileSdk | 30（Android 11） |
| dex | classes.dex ~ classes4.dex（5 个，含 HMS / jpush / 乐播投屏 SDK） |
| 参考模块 | Deer God 0.5.4（`com.wengui.hook`），LSPosed「会员通杀」模块，libxposed API + DexKit |

## Deer God 的架构（从 dex 里挖出来的常量）

包内字符串常量直接暴露了它的设计：

```
Lcom/wengui/hook/HookEntry;        // LSPosed 入口
Lcom/wengui/hook/Module101;        // libxposed API 101 模块体
Lcom/wengui/hook/Module;           // legacy Xposed 模块体
Lde/robv/android/xposed/IXposedHookLoadPackage;
Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;
Lde/robv/android/xposed/XC_MethodReplacement;
Lio/github/libxposed/api/XposedModule;      // 新 API
Lorg/luckypray/dexkit/DexKitBridge;         // 运行时 dex 检索

METHOD_IS_VIP                  // 目标方法名候选
METHOD_IS_SVIP
VIP_TYPE / VIP_A_STRICT
VIP_INT_KEYS                  // 整型会员键名表
VIP_STRING_KEYS               // 字符串键名表
VIP_CUTOFF                    // 到期时间截断值（改为超远期）
VIP_RESIDUE                   // 键值残留处理
expire_cnt / expired / expires
hookAllMethods / hookAllConstructors
```

结论：它的「通杀」= **键名矩阵 + 方法名矩阵 + 时间截断值**，用 DexKit 在运行时反查 `isVip*` 之类方法，再配合 SharedPreferences / JSON 层统一改写；`VIP_CUTOFF` 就是把所有到期时间顶到超远期（无限久）。本项目按同一思路实现，但不依赖 DexKit，改为静态分析得到的确定性 hook 点 + 键名矩阵兜底。

## 目标 App 的会员相关实体

### 1) 读书会员信息（服务端下发）
`com/dyw/bean/ReadVipBean$MemberInfoBean`

```
.field private desc:Ljava/lang/String;
.field private status:Ljava/lang/String;   // "3" = 会员中
```

`ReadVipFragment.C2()` 里 `status == "3"` 判定为「已开通会员」（埋点参数 1/0）。

### 2) 内容级 VIP 标记（客户端门禁的真正开关）
`com/dyw/bean/CourseDetailInfoBean`

```
.field public vipFlag:I          // isVip()      -> vipFlag == 1
.field public courseVipFlag:I     // isVipCourse()-> courseVipFlag == 1
```

反编译出的方法体：

```smali
.method public isVip()Z
    iget v0, p0, Lcom/dyw/bean/CourseDetailInfoBean;->vipFlag:I
    const/4 v1, 0x1
    if-ne v0, v1, :cond_0
    ...
```

同样结构存在于 `CourseDetailBean`（`vipFlag` / `courseVipFlag` / `isVipCourse`）、`CourseLessonInfoBean`（public `vipFlag` / `courseVipFlag`）。

### 3) 播放请求里的会员类型
`MediaPlayFragment.R2()`：

```smali
invoke-virtual {p1}, Lcom/dyw/bean/CourseDetailInfoBean;->isVipCourse()Z
if-eqz p1, :cond_0
const/4 p1, 0x2       # 2 = 会员课程
:cond_0
const/4 p1, 0x1       # 1 = 普通课程
invoke-virtual {v0, v1, v2, p1, v3}, Lcom/dy/common/presenter/MainPresenter;->z1(...)
```

把 `isVipCourse()` 钉死为 `false`，播放请求就会以「普通课程」身份发出，服务端不再按会员课拦截。

### 4) 会员中心 / 充值
`com/dyw/ui/fragment/Mine/vip/OpenVIPFragment`（`FragmentOpenVipBinding`：`tvVipTime`、`tvVipButton`、`ivMemberFlag`），
充值走 H5：`/h5/dyw/#/memberRecharge?accesstoken=`，会员详情接口 `/appv2/member/detail`（返回 JSON 字符串，客户端再解析）。

### 5) 第三方会员（顺带覆盖）
`com/hpplay/sdk/source/bean/VipAuthResultBean`、`VipAuthSetting`、`loginVipAuth`、`sendVIPQuery`、`hasVipFeature`（乐播投屏 VIP）。

## 攻击面归纳（决定 hook 点）

| 层 | 位置 | 处理 |
| --- | --- | --- |
| 内容门禁 | `CourseDetailInfoBean.isVip/isVipCourse`、`CourseDetailBean.isVipCourse` | 返回 false（会员内容变免费） |
| 会员状态 | `ReadVipBean$MemberInfoBean.getStatus` | 返回 "3"（已开通） |
| 持久层 | `SharedPreferencesImpl.get*`、`Editor.put*`、`MMKV.decode*/encode*` | 键名矩阵改写 |
| 网络层 | `org.json.JSONObject.getString/getInt/getBoolean/getLong/opt*` | 键名矩阵 + 时间截断 |
| 时间 | 任何到期类 long / 字符串 | 截断到 `4102444800`(s) / `4102444800000`(ms) / `2099-12-31 23:59:59` |

## 工具链（本机实际使用，全部可复现）

```
apktool 2.11.1          -> 解包/回编译
jadx 1.5.1 + Temurin 17 -> Java 级反编译
build-tools 34.0.0      -> aapt2 / d8 / zipalign / apksigner
platform-34 android.jar -> javac 编译期 android.* 桩
XposedBridgeAPI-82.jar  -> legacy Xposed API（阿里云 maven 镜像可取）
```

下载命令见 `tools/fetch-tools.sh`。
