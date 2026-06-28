package androidx.media3.demo.media;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/**
 * 应用入口。
 *
 * 主要职责：
 *  - 在所有 Activity 创建之前调 {@link DynamicColors#applyToActivitiesIfAvailable}，
 *    让 Android 12+ 设备的壁纸取色自动应用到我们的 Theme.Anxiong 主题。
 *  - （以后可以在这里挂全局 ANR / Crash 监听。）
 */
public class AnxiongApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 自动注册 ActivityLifecycleCallbacks，Activity 在 onCreate 时套上 Dynamic Color overlay
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
