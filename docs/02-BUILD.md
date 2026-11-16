# 02 · 模块工程与构建（无 Gradle 全手搓）

## 目录

```
DywVipUnlock/
├── AndroidManifest.xml          # LSPosed 模块声明（xposedmodule / xposedscope）
├── assets/xposed_init           # 指向 com.shenyi.dywunlock.HookEntry
├── res/values/{strings,arrays}.xml
├── src/com/shenyi/dywunlock/
│   ├── HookEntry.java           # 入口，只接管 com.dyw
│   ├── Const.java               # CUTOFF = 2100-01-01 / 2099-12-31
│   ├── VipKeys.java             # 键名矩阵（会员开关键 / 到期键）
│   ├── TimeNorm.java            # 时间截断（秒/毫秒/日期串）
│   ├── XLog.java                # 日志前缀 [DywUnlock]
│   ├── MainActivity.java        # 说明页
│   └── hooks/
│       ├── PrefHook.java        # SharedPreferences 读+写
│       ├── JsonHook.java        # org.json.JSONObject 取值点
│       ├── MmkvHook.java        # 腾讯 MMKV（若有）
│       ├── BeanHook.java        # 静态分析确认的定点 hook
│       └── GenericHook.java     # ClassLoader 兜底网
└── scripts/build.sh             # aapt2 -> javac -> d8 -> zipalign -> apksigner
```

## 构建链（每一步的产物）

| 步骤 | 命令 | 产物 |
| --- | --- | --- |
| 1 | `aapt2 compile --dir res -o build/res.zip` | 编译后的资源 flat |
| 2 | `aapt2 link -I android.jar --manifest AndroidManifest.xml -R res.zip --java gen` | `build/base.apk` + `R.java` |
| 3 | `javac -source 8 -target 8 -bootclasspath android.jar -cp XposedBridgeAPI-82.jar` | `build/classes/**.class` |
| 4 | `d8 --release --min-api 21 --lib android.jar --lib XposedBridgeAPI-82.jar` | `build/dex/classes.dex` |
| 5 | python zipfile 追加 `classes.dex` + `assets/xposed_init` | `build/app.apk` |
| 6 | `zipalign -f -p 4` | `build/app-aligned.apk` |
| 7 | `apksigner sign`（v1+v2+v3） | `dist/DywVipUnlock-v1.0.0.apk` |

## 实测构建输出

```
== [1/7] aapt2 compile 资源
== [2/7] aapt2 link 生成资源 APK
== [3/7] javac 编译（source/target 8，bootclasspath=android.jar）
== [4/7] d8 生成 classes.dex
== [5/7] 打包 dex + assets 进 APK
== [6/7] zipalign
== [7/7] 签名
Signer #1 certificate DN: CN=Android Debug, O=Android, C=US
产物: dist/DywVipUnlock-v1.0.0.apk  (20,947 bytes)
```

## APK 结构校验

```
AndroidManifest.xml      2864
resources.arsc            948
classes.dex             23436
assets/xposed_init         31
META-INF/…              v1 签名
```

`aapt2 dump badging`：

```
package: name='com.shenyi.dywunlock' versionCode='1' versionName='1.0.0'
sdkVersion:'21'  targetSdkVersion:'33'
application-label:'申怡读书·永久会员'
launchable-activity: com.shenyi.dywunlock.MainActivity
```

`aapt2 dump xmltree` 确认模块标记齐全：

```
meta-data name="xposedmodule"      value=true
meta-data name="xposeddescription" value=@string/xposed_description
meta-data name="xposedminversion"  value=82
meta-data name="xposedscope"       resource=@array/xposed_scope   (com.dyw)
activity intent-filter category="de.robv.android.xposed.category.MODULE_SETTINGS"
```
