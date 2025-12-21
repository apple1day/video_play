package androidx.media3.demo.filebrowser;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private PendingDeleteManager pendingDeleteManager;
    private TextView pendingCountText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        pendingDeleteManager = PendingDeleteManager.getInstance(this);
        
        // 设置监听器
        pendingDeleteManager.setOnFileDeletedListener(new PendingDeleteManager.OnFileDeletedListener() {
            @Override
            public void onFileDeleted(String filePath) {
                // 自动删除时显示提示
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, 
                        "已自动删除文件", 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onFilesDeleted(int count) {
                // 批量删除时显示提示
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, 
                        "已删除 " + count + " 个文件", 
                        Toast.LENGTH_SHORT).show();
                });
            }
        });

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

        // 待删除数量设置
        pendingCountText = findViewById(R.id.pending_count_text);
        Button decreaseButton = findViewById(R.id.decrease_button);
        Button increaseButton = findViewById(R.id.increase_button);

        updatePendingCountDisplay();

        decreaseButton.setOnClickListener(v -> {
            int current = pendingDeleteManager.getMaxPendingCount();
            if (current > 1) {
                pendingDeleteManager.setMaxPendingCount(current - 1);
                updatePendingCountDisplay();
            }
        });

        increaseButton.setOnClickListener(v -> {
            int current = pendingDeleteManager.getMaxPendingCount();
            if (current < 99) {
                pendingDeleteManager.setMaxPendingCount(current + 1);
                updatePendingCountDisplay();
            }
        });
    }

    private void updatePendingCountDisplay() {
        int count = pendingDeleteManager.getMaxPendingCount();
        pendingCountText.setText(String.valueOf(count));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除监听器
        if (pendingDeleteManager != null) {
            pendingDeleteManager.setOnFileDeletedListener(null);
        }
    }
}
