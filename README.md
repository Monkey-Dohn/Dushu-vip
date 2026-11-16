# 申怡读书 · 永久会员（LSPosed 模块）

给 **申怡读书（`com.dyw`，即 `1.apk`）** 做的会员解锁插件：**开通读书会员 + 到期时间改成无限久（2099-12-31）**。
实现思路照抄 **Deer God 0.5.4（`com.wengui.hook`，LSPosed「会员通杀」模块）** 的架构：
**键名矩阵 + 方法名矩阵 + 时间截断值**，只是把它的 DexKit 运行时检索换成了对本样本静态分析得到的确定性 hook 点，外加一层 ClassLoader 兜底网。

产物：`dist/DywVipUnlock-v1.0.0.apk`（20,947 B，v1+v2+v3 已签名）

---

## 一、Deer God 是怎么做到的（逆向结论）

从 `Deer God_0.5.4.apk` 的 dex 里直接挖出的常量就是它的设计图：

```
Lcom/wengui/hook/HookEntry;            // LSPosed 入口
Lcom/wengui/hook/Module101;            // libxposed API 101 模块体
Lde/robv/android/xposed/IXposedHookLoadPackage;
Lorg/luckypray/dexkit/DexKitBridge;    // 运行时 dex 检索
METHOD_IS_VIP / METHOD_IS_SVIP         // 目标方法名矩阵
VIP_INT_KEYS / VIP_STRING_KEYS         // 会员键名矩阵
VIP_CUTOFF / VIP_RESIDUE               // 到期时间截断值 / 残留处理
VIP_TYPE / VIP_A_STRICT
expire_cnt / expired / expires
```

三点照搬：

| Deer God | 本项目 |
| --- | --- |
| `METHOD_IS_VIP` 方法名矩阵 | `GenericHook` 在 ClassLoader 上兜底，命中 `isVip/isMember/hasVip…` |
| `VIP_INT_KEYS` / `VIP_STRING_KEYS` | `VipKeys`：精确键 + 词根组合键 |
| `VIP_CUTOFF` 时间顶到超远期 | `Const.CUTOFF_MS = 4102444800000`（2100-01-01）、日期串 `2099-12-31 23:59:59` |

## 二、com.dyw 的会员门禁（静态分析结论）

| 位置 | 语义 | 本项目处理 |
| --- | --- | --- |
| `com.dyw.bean.CourseDetailInfoBean.isVip()` | `vipFlag == 1` → 会员书 | 返回 `false` → 会员书按免费放行 |
| `com.dyw.bean.CourseDetailInfoBean.isVipCourse()` | `courseVipFlag == 1` → 会员课 | 返回 `false` |
| `com.dyw.bean.CourseDetailBean.isVipCourse()` | 同上 | 返回 `false` |
| `MediaPlayFragment.R2()` | 按 `isVipCourse()` 决定请求 courseType=2(会员)/1(普通) | 被上面钉成 1 → 普通课程身份请求 |
| `com.dyw.bean.ReadVipBean$MemberInfoBean.getStatus()` | `"3"` = 会员中（`ReadVipFragment.C2` 判定） | 返回 `"3"` |
| `getDesc()` | 会员描述 | 空则给 `永久会员 · 无限期` |
| `/appv2/member/detail` | 会员详情 JSON 字符串 | 在 `org.json.JSONObject` 取值点改写 |
| SharedPreferences / MMKV | 本地会员缓存 | 读写双向改写 |
| H5 `/h5/dyw/#/memberRecharge` | 购买页 | 见 `docs/04-NETWORK-H5.md`（WebView 注入方案） |

## 三、模块做了什么（5 层 hook）

1. **PrefHook** — `android.app.SharedPreferencesImpl.getBoolean/getInt/getLong/getString` 直接返回会员值；
   `EditorImpl.put*` 落盘前改写，防止 App 把 0 写回去。
2. **JsonHook** — `org.json.JSONObject` 的 `get*/opt*` 在服务端响应解析处改写；
   到期键统一顶到 `4102444800000` / `2099-12-31 23:59:59`。
3. **MmkvHook** — 若目标集成 `com.tencent.mmkv.MMKV`，`decode*/encode` 同样改写（未集成则跳过）。
4. **BeanHook** — 静态分析确认的 5 个定点：两个 `isVip*`、一个 `isVipCourse`、`getStatus`、`getDesc`，
   以及 `vipFlag/courseVipFlag` 的 setter/getter 归零。
5. **GenericHook** — 挂 `ClassLoader.loadClass`，只对 `com.dyw.` / `com.dy.` 下名字含
   `user/account/memberinfo/vipinfo/login/profile` 的实体做兜底：0 参 boolean 的 `isVip*` → true，
   0 参 long 的 `getExpire*/getEndTime` → `CUTOFF_MS`。内容实体不含这些词，不会被误判。

时间语义（"无限久"）：

```java
CUTOFF_MS   = 4102444800000L   // 2100-01-01 00:00:00 UTC
CUTOFF_SEC  = 4102444800L
CUTOFF_DATE = "2099-12-31 23:59:59"
CUTOFF_DAY  = "2099-12-31"
```

`TimeNorm` 只在键名命中"会员到期"时才截断，避免误伤正文发布时间一类的普通时间戳。

## 四、构建

```bash
bash tools/fetch-tools.sh        # apktool / jadx / JDK17 / build-tools34 / platform34 / Xposed API82
bash scripts/build.sh            # -> dist/DywVipUnlock-v1.0.0.apk
```

构建链是手搓的，不依赖 Gradle：`aapt2 compile → aapt2 link → javac(source 8) → d8 → zip → zipalign → apksigner`。
细节与实测输出见 [docs/02-BUILD.md](docs/02-BUILD.md)。

## 五、安装与验证

```bash
adb install -r dist/DywVipUnlock-v1.0.0.apk
# LSPosed 里启用模块，作用域勾 com.dyw
adb shell am force-stop com.dyw
adb logcat | grep DywUnlock        # 看命中记录
```

完整验证清单（日志 / 界面 / 数据三路证据）见 [docs/03-INSTALL.md](docs/03-INSTALL.md)。

## 六、上传 GitHub（每一步一个提交）

本仓库已经是分步提交的：

```
step01  侦察 1.apk(com.dyw) 与 Deer God 结构 + 工具链脚本
step02  模块工程骨架（Manifest / xposed_init / 构建脚本）
step03  hook 实现（Pref / Json / MMKV / Bean / Generic）
step04  构建产物 + 构建/安装/网络层文档
step05  README + 一键推送脚本
```

推送：

```bash
export GITHUB_TOKEN=ghp_xxxxxxxx        # 有 repo 权限
bash scripts/push-github.sh <你的用户名> <仓库名> private
```

不带 token 时脚本只配置 remote，再手动 `git push -u origin main` 即可。

## 七、目录

```
AndroidManifest.xml            模块声明（xposedmodule/xposedscope=com.dyw）
assets/xposed_init             入口类
src/com/shenyi/dywunlock/      入口/常量/键名矩阵/时间截断/日志/说明页
src/com/shenyi/dywunlock/hooks 五层 hook
res/values/                    字符串与作用域数组
scripts/build.sh               构建
scripts/push-github.sh         推送 GitHub
docs/01-ANALYSIS.md            目标与 Deer God 逆向记录
docs/02-BUILD.md               构建链与实测输出
docs/03-INSTALL.md             安装/验证/回滚
docs/04-NETWORK-H5.md          H5 与 OkHttp 补层方案
dist/DywVipUnlock-v1.0.0.apk   成品
```
