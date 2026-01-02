package androidx.media3.demo.media;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private PendingDeleteManager pendingDeleteManager;
    private TextView pendingCountText;
    private LinearLayout storageInfoLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        pendingDeleteManager = PendingDeleteManager.getInstance(this);
        storageInfoLayout = findViewById(R.id.storage_info_layout);
        
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
        
        // 加载存储信息
        loadStorageInfo();

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
    
    private void loadStorageInfo() {
        new Thread(() -> {
            List<File> storageRoots = FileUtils.getStorageDirectories(this);
            
            runOnUiThread(() -> {
                storageInfoLayout.removeAllViews();
                
                for (File directory : storageRoots) {
                    FileUtils.StorageInfo info = FileUtils.getStorageInfo(directory);
                    String title = getStorageTitle(directory);
                    View infoView = createStorageInfoView(title, info, directory);
                    storageInfoLayout.addView(infoView);
                }
            });
        }).start();
    }
    
    private String getStorageTitle(File directory) {
        File dataDir = Environment.getDataDirectory();
        File externalStorage = Environment.getExternalStorageDirectory();
        
        if (directory.equals(dataDir)) {
            return getString(R.string.internal_storage);
        } else if (directory.equals(externalStorage)) {
            return getString(R.string.external_storage);
        } else {
            String path = directory.getAbsolutePath();
            if (path.contains("/storage/")) {
                String[] parts = path.split("/");
                if (parts.length > 2) {
                    return "外置存储: " + parts[parts.length - 1];
                }
            }
            return "存储设备: " + directory.getName();
        }
    }
    
    private View createStorageInfoView(String title, FileUtils.StorageInfo info, File directory) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 0, 0, 16);
        
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 4);
        layout.addView(titleView);
        
        TextView pathView = new TextView(this);
        pathView.setText(directory.getAbsolutePath());
        pathView.setTextSize(12);
        pathView.setTextColor(0xFF666666);
        pathView.setPadding(0, 0, 0, 4);
        layout.addView(pathView);
        
        TextView totalView = new TextView(this);
        totalView.setText(getString(R.string.total_space) + ": " + info.getFormattedTotal());
        totalView.setTextSize(12);
        totalView.setPadding(0, 0, 0, 2);
        layout.addView(totalView);
        
        TextView usedView = new TextView(this);
        usedView.setText(getString(R.string.used_space) + ": " + info.getFormattedUsed());
        usedView.setTextSize(12);
        usedView.setPadding(0, 0, 0, 2);
        layout.addView(usedView);
        
        TextView freeView = new TextView(this);
        freeView.setText(getString(R.string.free_space) + ": " + info.getFormattedFree());
        freeView.setTextSize(12);
        layout.addView(freeView);
        
        return layout;
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
