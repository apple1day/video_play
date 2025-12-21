package androidx.media3.demo.filebrowser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

/**
 * 权限管理器 - 处理 MANAGE_EXTERNAL_STORAGE 权限
 */
public class PermissionManager {
    
    private static final String TAG = "PermissionManager";
    public static final int REQUEST_MANAGE_STORAGE = 1002;
    
    /**
     * 检查是否有管理所有文件的权限
     */
    public static boolean hasManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasPermission = Environment.isExternalStorageManager();
            Log.d(TAG, "hasManageStoragePermission: " + hasPermission);
            return hasPermission;
        }
        // Android 10 及以下不需要此权限
        return true;
    }
    
    /**
     * 请求管理所有文件权限
     */
    public static void requestManageStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showPermissionDialog(activity);
            }
        }
    }
    
    /**
     * 显示权限说明对话框
     */
    private static void showPermissionDialog(Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("需要文件管理权限")
            .setMessage("为了删除文件，需要授予\"管理所有文件\"权限。\n\n" +
                "点击\"前往设置\"后，请选择\"允许管理所有文件\"。")
            .setPositiveButton("前往设置", (dialog, which) -> {
                openManageStorageSettings(activity);
            })
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show();
    }
    
    /**
     * 打开管理存储权限设置页面
     */
    public static void openManageStorageSettings(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                Log.d(TAG, "openManageStorageSettings: 打开设置页面");
            } catch (Exception e) {
                Log.e(TAG, "openManageStorageSettings: 无法打开设置页面", e);
                // 如果失败，打开通用的应用设置页面
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                } catch (Exception ex) {
                    Log.e(TAG, "openManageStorageSettings: 也无法打开应用设置", ex);
                }
            }
        }
    }
    
    /**
     * 检查并请求权限（如果需要）
     * @return true 如果已有权限，false 如果需要请求
     */
    public static boolean checkAndRequestIfNeeded(Activity activity) {
        if (!hasManageStoragePermission()) {
            requestManageStoragePermission(activity);
            return false;
        }
        return true;
    }
}
