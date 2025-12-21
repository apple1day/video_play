package androidx.media3.demo.filebrowser;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 返回按钮
        View backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // 设置版本信息
        TextView versionText = findViewById(R.id.version_text);
        TextView packageNameText = findViewById(R.id.package_name_text);
        TextView buildTimeText = findViewById(R.id.build_time_text);
        TextView deviceInfoText = findViewById(R.id.device_info_text);

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionText.setText(packageInfo.versionName + " (" + packageInfo.versionCode + ")");
            packageNameText.setText(getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("未知");
            packageNameText.setText("未知");
        }

        // 设置编译时间（从 BuildConfig 获取或使用当前时间）
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        buildTimeText.setText(sdf.format(new Date()));

        // 设置设备信息
        String deviceInfo = Build.BRAND + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
        deviceInfoText.setText(deviceInfo);
    }
}
