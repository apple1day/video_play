package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 管理待删除的视频文件
 */
public class PendingDeleteManager {
    private static final String TAG = "PendingDeleteManager";
    private static final String PREFS_NAME = "pending_deletes";
    private static final String KEY_PENDING_FILES = "pending_delete_files";
    private static final String KEY_MAX_COUNT = "max_pending_count";
    private static final int DEFAULT_MAX_COUNT = 5;
    
    private static PendingDeleteManager instance;
    private SharedPreferences prefs;
    private Context context;
    private ExecutorService deleteExecutor;
    private Handler mainHandler;
    private OnFileDeletedListener onFileDeletedListener;
    
    /**
     * 文件删除监听器
     */
    public interface OnFileDeletedListener {
        void onFileDeleted(String filePath);
        void onFilesDeleted(int count);
    }
    
    private PendingDeleteManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.deleteExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized PendingDeleteManager getInstance(Context context) {
        if (instance == null) {
            instance = new PendingDeleteManager(context);
        }
        return instance;
    }
    
    /**
     * 设置文件删除监听器
     */
    public void setOnFileDeletedListener(OnFileDeletedListener listener) {
        this.onFileDeletedListener = listener;
    }
    
    /**
     * 检查文件是否在待删除列表中
     */
    public boolean isPendingDelete(String filePath) {
        Set<String> pendingFiles = getPendingFiles();
        return pendingFiles.contains(filePath);
    }
    
    /**
     * 添加文件到待删除列表
     * 如果超过最大数量，会自动删除最早添加的文件
     */
    public void addPendingDelete(String filePath) {
        LinkedHashSet<String> pendingFiles = new LinkedHashSet<>(getPendingFiles());
        
        if (!pendingFiles.contains(filePath)) {
            pendingFiles.add(filePath);
            
            // 检查是否超过最大数量
            int maxCount = getMaxPendingCount();
            while (pendingFiles.size() > maxCount && !pendingFiles.isEmpty()) {
                // 删除最早添加的文件（同步删除）
                String firstFile = pendingFiles.iterator().next();
                pendingFiles.remove(firstFile);
                
                // 立即删除文件
                File file = new File(firstFile);
                if (file.exists()) {
                    boolean deleted = file.delete();
                    Log.d(TAG, "addPendingDelete: 超过最大数量，删除文件 " + file.getName() + ", 结果: " + deleted);
                    
                    // 通知监听器
                    if (deleted && onFileDeletedListener != null) {
                        mainHandler.post(() -> onFileDeletedListener.onFileDeleted(firstFile));
                    }
                } else {
                    Log.w(TAG, "addPendingDelete: 文件不存在 - " + firstFile);
                }
            }
            
            savePendingFiles(pendingFiles);
            Log.d(TAG, "addPendingDelete: 当前待删除数量 = " + pendingFiles.size());
        }
    }
    
    /**
     * 从待删除列表中移除文件
     */
    public void removePendingDelete(String filePath) {
        Set<String> pendingFiles = getPendingFiles();
        if (pendingFiles.remove(filePath)) {
            savePendingFiles(pendingFiles);
        }
    }
    
    /**
     * 切换待删除状态
     */
    public boolean togglePendingDelete(String filePath) {
        if (isPendingDelete(filePath)) {
            removePendingDelete(filePath);
            return false;
        } else {
            addPendingDelete(filePath);
            return true;
        }
    }
    
    /**
     * 获取所有待删除的文件
     */
    public List<String> getPendingDeleteList() {
        return new ArrayList<>(getPendingFiles());
    }
    
    /**
     * 获取待删除文件数量
     */
    public int getPendingCount() {
        return getPendingFiles().size();
    }
    
    /**
     * 删除所有待删除标记的文件
     */
    public void deleteAllPending() {
        List<String> pendingFiles = getPendingDeleteList();
        if (pendingFiles.isEmpty()) {
            return;
        }
        
        final int totalCount = pendingFiles.size();
        
        // 异步删除所有文件
        deleteExecutor.execute(() -> {
            int successCount = 0;
            
            for (String filePath : pendingFiles) {
                if (deleteFile(filePath)) {
                    successCount++;
                }
            }
            
            // 清空待删除列表
            clearAllPending();
            
            // 在主线程回调
            final int finalSuccessCount = successCount;
            mainHandler.post(() -> {
                if (onFileDeletedListener != null) {
                    onFileDeletedListener.onFilesDeleted(finalSuccessCount);
                }
            });
        });
    }
    
    /**
     * 清空待删除列表（不删除文件）
     */
    public void clearAllPending() {
        prefs.edit().remove(KEY_PENDING_FILES).apply();
    }
    
    /**
     * 获取最大待删除数量设置
     */
    public int getMaxPendingCount() {
        return prefs.getInt(KEY_MAX_COUNT, DEFAULT_MAX_COUNT);
    }
    
    /**
     * 设置最大待删除数量
     */
    public void setMaxPendingCount(int count) {
        if (count < 1) {
            count = 1;
        }
        prefs.edit().putInt(KEY_MAX_COUNT, count).apply();
        
        // 检查当前待删除列表是否超过新的最大值
        LinkedHashSet<String> pendingFiles = new LinkedHashSet<>(getPendingFiles());
        while (pendingFiles.size() > count && !pendingFiles.isEmpty()) {
            // 删除最早添加的文件（同步删除）
            String firstFile = pendingFiles.iterator().next();
            pendingFiles.remove(firstFile);
            
            // 立即删除文件
            File file = new File(firstFile);
            if (file.exists()) {
                boolean deleted = file.delete();
                Log.d(TAG, "setMaxPendingCount: 超过新的最大值，删除文件 " + file.getName() + ", 结果: " + deleted);
                
                // 通知监听器
                if (deleted && onFileDeletedListener != null) {
                    mainHandler.post(() -> onFileDeletedListener.onFileDeleted(firstFile));
                }
            } else {
                Log.w(TAG, "setMaxPendingCount: 文件不存在 - " + firstFile);
            }
        }
        savePendingFiles(pendingFiles);
        Log.d(TAG, "setMaxPendingCount: 设置为 " + count + ", 当前待删除数量 = " + pendingFiles.size());
    }
    
    /**
     * 获取待删除文件集合
     */
    private Set<String> getPendingFiles() {
        return prefs.getStringSet(KEY_PENDING_FILES, new LinkedHashSet<>());
    }
    
    /**
     * 保存待删除文件集合
     */
    private void savePendingFiles(Set<String> files) {
        // SharedPreferences 要求使用新的 Set 对象
        prefs.edit().putStringSet(KEY_PENDING_FILES, new LinkedHashSet<>(files)).apply();
    }
    
    /**
     * 异步删除单个文件
     */
    private void deleteFileAsync(String filePath) {
        deleteExecutor.execute(() -> {
            boolean deleted = deleteFile(filePath);
            if (deleted) {
                // 在主线程回调
                mainHandler.post(() -> {
                    if (onFileDeletedListener != null) {
                        onFileDeletedListener.onFileDeleted(filePath);
                    }
                });
            }
        });
    }
    
    /**
     * 删除单个文件（同步方法，在后台线程调用）
     */
    private boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Log.w(TAG, "deleteFile: 文件不存在 - " + filePath);
            return false;
        }
        
        boolean deleted = false;
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 尝试使用 MediaStore API
                // 这里简化处理，直接使用 File.delete()
                deleted = file.delete();
            } else {
                // Android 9 及以下
                deleted = file.delete();
            }
            
            if (deleted) {
                Log.d(TAG, "deleteFile: 删除成功 - " + file.getName());
            } else {
                Log.e(TAG, "deleteFile: 删除失败 - " + file.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "deleteFile: 删除异常 - " + file.getName(), e);
        }
        
        return deleted;
    }
    
    /**
     * 清理资源
     */
    public void shutdown() {
        if (deleteExecutor != null && !deleteExecutor.isShutdown()) {
            deleteExecutor.shutdown();
        }
    }
}
