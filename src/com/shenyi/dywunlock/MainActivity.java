package com.shenyi.dywunlock;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 一个极简说明页：安装后在桌面/LSPosed 里能看到模块状态 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        TextView tv = new TextView(this);
        tv.setTextSize(15f);
        tv.setText(
            "申怡读书 · 永久会员（LSPosed 模块）\n\n"
            + "作用域：com.dyw\n\n"
            + "生效内容\n"
            + "· 读书会员状态 -> 已开通（status=\"3\"）\n"
            + "· 会员到期时间 -> 2099-12-31 23:59:59（无限久）\n"
            + "· 会员课程 vipFlag/courseVipFlag -> 0，按免费内容放行\n"
            + "· SharedPreferences / MMKV / JSON 三层键名矩阵统改\n\n"
            + "使用步骤\n"
            + "1. LSPosed 里启用本模块，作用域勾选 申怡读书\n"
            + "2. 强制停止 申怡读书 后重新打开\n"
            + "3. LSPosed 日志过滤 DywUnlock 可看到命中记录\n"
        );
        box.addView(tv);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        setContentView(sv);
    }
}
