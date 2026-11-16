package com.shenyi.dywunlock;

import com.shenyi.dywunlock.hooks.BeanHook;
import com.shenyi.dywunlock.hooks.GenericHook;
import com.shenyi.dywunlock.hooks.JsonHook;
import com.shenyi.dywunlock.hooks.MmkvHook;
import com.shenyi.dywunlock.hooks.PrefHook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 模块入口（assets/xposed_init 指向本类）。
 *
 * 只对 com.dyw（申怡读书）生效，避免污染其它 App。
 */
public class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!Const.TARGET_PKG.equals(lpparam.packageName)) return;

        long t0 = System.currentTimeMillis();
        XLog.i("==== 进入 " + lpparam.packageName + " (" + lpparam.processName + ") ====");

        ClassLoader cl = lpparam.classLoader;
        try {
            PrefHook.install(cl);
        } catch (Throwable t) { XLog.e("PrefHook 安装失败", t); }
        try {
            JsonHook.install(cl);
        } catch (Throwable t) { XLog.e("JsonHook 安装失败", t); }
        try {
            MmkvHook.install(cl);
        } catch (Throwable t) { XLog.e("MmkvHook 安装失败", t); }
        try {
            BeanHook.install(cl);
        } catch (Throwable t) { XLog.e("BeanHook 安装失败", t); }
        try {
            GenericHook.install(cl);
        } catch (Throwable t) { XLog.e("GenericHook 安装失败", t); }

        XLog.i("==== hook 安装完成，耗时 " + (System.currentTimeMillis() - t0) + "ms，会员=永久 ====");
    }
}
