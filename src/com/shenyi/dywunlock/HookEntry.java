package com.shenyi.dywunlock;

import com.shenyi.dywunlock.hooks.BeanHook;
import com.shenyi.dywunlock.hooks.GenericHook;
import com.shenyi.dywunlock.hooks.JsonHook;
import com.shenyi.dywunlock.hooks.MmkvHook;
import com.shenyi.dywunlock.hooks.NetHook;
import com.shenyi.dywunlock.hooks.PrefHook;
import com.shenyi.dywunlock.hooks.UiHook;
import com.shenyi.dywunlock.hooks.UserInfoHook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 模块入口（assets/xposed_init 指向本类）。
 *
 * 只对 com.dyw（申怡读书）生效，避免污染其它 App。
 * 每一层单独 try/catch + 单独日志，方便在真机上定位崩溃点。
 */
public class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!Const.TARGET_PKG.equals(lpparam.packageName)) return;

        long t0 = System.currentTimeMillis();
        XLog.i("==== 进入 " + lpparam.packageName + " (" + lpparam.processName + ") ====");

        ClassLoader cl = lpparam.classLoader;

        if (Const.ENABLE_PREF) {
            XLog.i("step: PrefHook ...");
            try { PrefHook.install(cl); } catch (Throwable t) { XLog.e("PrefHook 安装失败", t); }
            XLog.i("step: PrefHook done");
        }
        if (Const.ENABLE_JSON) {
            XLog.i("step: JsonHook ...");
            try { JsonHook.install(cl); } catch (Throwable t) { XLog.e("JsonHook 安装失败", t); }
            XLog.i("step: JsonHook done");
        }
        if (Const.ENABLE_MMKV) {
            XLog.i("step: MmkvHook ...");
            try { MmkvHook.install(cl); } catch (Throwable t) { XLog.e("MmkvHook 安装失败", t); }
            XLog.i("step: MmkvHook done");
        }
        if (Const.ENABLE_BEAN) {
            XLog.i("step: BeanHook ...");
            try { BeanHook.install(cl); } catch (Throwable t) { XLog.e("BeanHook 安装失败", t); }
            XLog.i("step: BeanHook done");
        }
        if (Const.ENABLE_USERINFO) {
            XLog.i("step: UserInfoHook ...");
            try { UserInfoHook.install(cl); } catch (Throwable t) { XLog.e("UserInfoHook 安装失败", t); }
            XLog.i("step: UserInfoHook done");
        }
        if (Const.ENABLE_UI) {
            XLog.i("step: UiHook ...");
            try { UiHook.install(cl); } catch (Throwable t) { XLog.e("UiHook 安装失败", t); }
            XLog.i("step: UiHook done");
        }
        if (Const.ENABLE_NET) {
            XLog.i("step: NetHook ...");
            try { NetHook.install(cl); } catch (Throwable t) { XLog.e("NetHook 安装失败", t); }
            XLog.i("step: NetHook done");
        }
        if (Const.ENABLE_GENERIC) {
            XLog.i("step: GenericHook ...");
            try { GenericHook.install(cl); } catch (Throwable t) { XLog.e("GenericHook 安装失败", t); }
            XLog.i("step: GenericHook done");
        }

        XLog.i("==== hook 安装完成，耗时 " + (System.currentTimeMillis() - t0) + "ms，会员=永久 ====");
    }
}
