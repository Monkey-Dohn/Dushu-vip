# 05 · 符号自检（hook 目标是否真的存在）

模块里每个 hook 点都在构建后回查了一次目标样本 `1.apk`（`work/apk1_smali/`）和产物 dex。

## 5.1 定点 hook 目标

| 目标 | 形态 | 结果 |
| --- | --- | --- |
| `com.dyw.bean.CourseDetailInfoBean.isVip()Z` | 方法 | ✅ 存在（`smali/com/dyw/bean/CourseDetailInfoBean.smali:510`） |
| `com.dyw.bean.CourseDetailInfoBean.isVipCourse()Z` | 方法 | ✅ 存在（`:529`） |
| `com.dyw.bean.CourseDetailBean.isVipCourse()Z` | 方法 | ✅ 存在（`:700`） |
| `com.dyw.bean.CourseDetailBean.getVipFlag()I / setVipFlag(I)V` | 方法 | ✅ 存在（`:558` / `:737`） |
| `com.dyw.bean.CourseLessonInfoBean.isTryLesson()Z` | 方法 | ✅ 存在（`:258`），`MediaPlayFragment` 6 处调用 |
| `com.dyw.bean.CourseLessonInfoBean.showTryLearn()Z` | 方法 | ✅ 存在（`:317`），`MediaPlayFragment:5795` 等调用 |
| `com.dyw.bean.ReadVipBean$MemberInfoBean.getStatus()Ljava/lang/String;` | 方法 | ✅ 存在（`ReadVipFragment.C2` 用它判 `"3"`） |
| `com.dyw.bean.ReadVipBean$MemberInfoBean.getDesc()` | 方法 | ✅ 存在 |
| `org.json.JSONObject` | 框架类 | ✅ 标准库 |
| `android.app.SharedPreferencesImpl` / `$EditorImpl` | 框架隐藏类 | ✅ AOSP 类名，`XposedHelpers.findClassIfExists` 取 |
| `com.tencent.mmkv.MMKV` | 第三方 | ❌ 目标未集成 → `MmkvHook` 自动跳过（日志会打印"未集成 MMKV"） |
| `okhttp3` | 第三方 | ✅ 存在（`smali_classes4/okhttp3`），`docs/04` 的补层方案可用 |

## 5.2 产物 dex 自检

```bash
unzip -p dist/DywVipUnlock-v1.0.0.apk classes.dex > build/out.dex
python -c "
data=open('build/out.dex','rb').read()
for k in [b'isTryLesson',b'showTryLearn',b'CourseLessonInfoBean',b'MemberInfoBean',b'DywUnlock']:
    print(k.decode(), k in data)
print('dex size', len(data))"
```

输出：

```
isTryLesson True
showTryLearn True
CourseLessonInfoBean True
MemberInfoBean True
DywUnlock True
dex size 23492
```

## 5.3 签名校验

```
Signer #1 certificate DN: CN=Android Debug, O=Android, C=US
Signer #1 SHA-256: ee9a277f82f9a47aa0598ff0bf43860ab2df7a6febe9b5846bd1384722d24365
v1 / v2 / v3 均已启用
```

## 5.4 已知边界

1. **试听分片**：`isTryLesson/showTryLearn` 归零后客户端不会再截断，但服务端若只签发试听地址，仍需 `docs/04` 的响应改写。
2. **H5 购买页**：会员中心部分区块是 WebView，需 `shouldInterceptRequest` 或 `evaluateJavascript` 注入。
3. **签名校验**：目标未观察到自校验（`requestSinkVipInfo sign not pass` 属于乐播 SDK 的通信签名，与模块无关）；若新版加上签名自检，本模块不改 APK 本体，不受影响。
