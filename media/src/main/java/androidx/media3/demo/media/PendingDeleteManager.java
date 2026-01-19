package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
        void onFileDeleted(String filePath, boolean success, String errorMessage);
        void onFilesDeleted(int successCount, int failCount, List<String> failedFiles);
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
                
                // 立即删除文件
                DeleteResult result = deleteFileSync(firstFile);
                Log.d(TAG, "addPendingDelete: 超过最大数量，删除文件 " + new File(firstFile).getName() 
                    + ", 结果: " + result.success + ", 原因: " + result.errorMessage);
                
                // 只有删除成功才从待删除列表中移除
                if (result.success) {
                    pendingFiles.remove(firstFile);
                } else {
                    // 删除失败，保留待删除标记，但需要跳出循环避免死循环
                    Log.w(TAG, "addPendingDelete: 删除失败，保留待删除标记 - " + new File(firstFile).getName());
                    break;
                }
                
                // 通知监听器
                if (onFileDeletedListener != null) {
                    final String file = firstFile;
                    final boolean success = result.success;
                    final String error = result.errorMessage;
                    mainHandler.post(() -> onFileDeletedListener.onFileDeleted(file, success, error));
                }
                
                // 显示删除结果
                showDeleteResult(firstFile, result);
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
            int failCount = 0;
            List<String> failedFiles = new ArrayList<>();
            List<String> failedFilePaths = new ArrayList<>();
            
            for (String filePath : pendingFiles) {
                DeleteResult result = deleteFileSync(filePath);
                if (result.success) {
                    successCount++;
                    // 删除成功，从待删除列表中移除
                    removePendingDelete(filePath);
                } else {
                    failCount++;
                    failedFiles.add(new File(filePath).getName() + ": " + result.errorMessage);
                    failedFilePaths.add(filePath);
                    // 删除失败，保留待删除标记
                    Log.w(TAG, "deleteAllPending: 删除失败，保留待删除标记 - " + new File(filePath).getName());
                }
                Log.d(TAG, "deleteAllPending: " + new File(filePath).getName() 
                    + ", 成功: " + result.success + ", 原因: " + result.errorMessage);
            }
            
            // 在主线程回调和显示结果
            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;
            final List<String> finalFailedFiles = new ArrayList<>(failedFiles);
            
            mainHandler.post(() -> {
                if (onFileDeletedListener != null) {
                    onFileDeletedListener.onFilesDeleted(finalSuccessCount, finalFailCount, finalFailedFiles);
                }
                
                // 显示删除结果
                StringBuilder message = new StringBuilder();
                message.append("删除完成：成功 ").append(finalSuccessCount).append(" 个");
                if (finalFailCount > 0) {
                    message.append("，失败 ").append(finalFailCount).append(" 个（保留待删除标记）");
                }
                Toast.makeText(context, message.toString(), Toast.LENGTH_LONG).show();
                
                // 如果有失败的文件，记录详细信息
                if (!finalFailedFiles.isEmpty()) {
                    Log.e(TAG, "删除失败的文件（已保留待删除标记）：");
                    for (String failInfo : finalFailedFiles) {
                        Log.e(TAG, "  - " + failInfo);
                    }
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
            
            // 立即删除文件
            DeleteResult result = deleteFileSync(firstFile);
            Log.d(TAG, "setMaxPendingCount: 超过新的最大值，删除文件 " + new File(firstFile).getName() 
                + ", 结果: " + result.success + ", 原因: " + result.errorMessage);
            
            // 只有删除成功才从待删除列表中移除
            if (result.success) {
                pendingFiles.remove(firstFile);
            } else {
                // 删除失败，保留待删除标记，但需要跳出循环避免死循环
                Log.w(TAG, "setMaxPendingCount: 删除失败，保留待删除标记 - " + new File(firstFile).getName());
                break;
            }
            
            // 通知监听器
            if (onFileDeletedListener != null) {
                final String file = firstFile;
                final boolean success = result.success;
                final String error = result.errorMessage;
                mainHandler.post(() -> onFileDeletedListener.onFileDeleted(file, success, error));
            }
            
            // 显示删除结果
            showDeleteResult(firstFile, result);
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
            DeleteResult result = deleteFileSync(filePath);
            // 在主线程回调
            mainHandler.post(() -> {
                if (onFileDeletedListener != null) {
                    onFileDeletedListener.onFileDeleted(filePath, result.success, result.errorMessage);
                }
            });
        });
    }
    
    /**
     * 删除结果类
     */
    private static class DeleteResult {
        boolean success;
        String errorMessage;
        
        DeleteResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * 删除单个文件（同步方法，在后台线程调用）
     * @return DeleteResult 包含删除结果和错误信息
     */
    private DeleteResult deleteFileSync(String filePath) {
        File file = new File(filePath);
        String fileName = file.getName();
        
        // 1. 检查文件是否存在
        if (!file.exists()) {
            String error = "文件不存在";
            Log.w(TAG, "deleteFile: " + error + " - " + fileName);
            return new DeleteResult(false, error);
        }
        
        // 2. 检查文件是否可读
        if (!file.canRead()) {
            String error = "文件无法读取（权限不足）";
            Log.e(TAG, "deleteFile: " + error + " - " + fileName);
            return new DeleteResult(false, error);
        }
        
        // 3. 检查文件是否可写（删除需要写权限）
        if (!file.canWrite()) {
            String error = "文件无法写入（权限不足，无法删除）";
            Log.e(TAG, "deleteFile: " + error + " - " + fileName);
            return new DeleteResult(false, error);
        }
        
        // 4. 检查父目录是否可写
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.canWrite()) {
            String error = "父目录无写权限";
            Log.e(TAG, "deleteFile: " + error + " - " + fileName + " (父目录: " + parentDir.getPath() + ")");
            return new DeleteResult(false, error);
        }
        
        // 5. 尝试删除文件
        boolean deleted = false;
        String errorMessage = null;
        
        try {
            deleted = file.delete();
            
            if (deleted) {
                // 等待一小段时间，让系统完成删除操作
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // 忽略
                }
                
                // 检查文件是否真的被删除了
                if (file.exists()) {
                    // 文件仍然存在，可能是0字节占位文件
                    Log.w(TAG, "deleteFile: 文件删除后仍存在，尝试再次删除 - " + fileName);
                    
                    // 再次尝试删除
                    deleted = file.delete();
                    
                    // 再次检查
                    if (file.exists()) {
                        // 如果是0字节文件，也认为删除成功
                        if (file.length() == 0) {
                            Log.d(TAG, "deleteFile: ✓ 删除成功（残留0字节文件已清理） - " + fileName);
                            return new DeleteResult(true, "删除成功");
                        } else {
                            errorMessage = "删除失败（文件仍然存在，大小: " + file.length() + " bytes）";
                            Log.e(TAG, "deleteFile: ✗ " + errorMessage + " - " + fileName);
                            return new DeleteResult(false, errorMessage);
                        }
                    }
                }
                
                Log.d(TAG, "deleteFile: ✓ 删除成功 - " + fileName);
                
                // 通知媒体扫描器更新
                notifyMediaStoreDeleted(file);
                
                return new DeleteResult(true, "删除成功");
            } else {
                // 删除失败，但没有抛出异常
                // 检查文件是否已经不存在了
                if (!file.exists()) {
                    Log.d(TAG, "deleteFile: ✓ 文件已不存在 - " + fileName);
                    return new DeleteResult(true, "删除成功");
                }
                
                errorMessage = "删除失败（可能被其他程序占用或系统保护）";
                Log.e(TAG, "deleteFile: ✗ " + errorMessage + " - " + fileName);
                
                // 打印详细的文件信息以帮助调试
                Log.e(TAG, "  文件路径: " + file.getAbsolutePath());
                Log.e(TAG, "  文件大小: " + file.length() + " bytes");
                Log.e(TAG, "  是否为文件: " + file.isFile());
                Log.e(TAG, "  是否为目录: " + file.isDirectory());
                Log.e(TAG, "  可读: " + file.canRead());
                Log.e(TAG, "  可写: " + file.canWrite());
                Log.e(TAG, "  可执行: " + file.canExecute());
                
                if (parentDir != null) {
                    Log.e(TAG, "  父目录: " + parentDir.getAbsolutePath());
                    Log.e(TAG, "  父目录可写: " + parentDir.canWrite());
                }
                
                return new DeleteResult(false, errorMessage);
            }
        } catch (SecurityException e) {
            errorMessage = "安全异常：权限被拒绝 - " + e.getMessage();
            Log.e(TAG, "deleteFile: " + errorMessage + " - " + fileName, e);
            return new DeleteResult(false, errorMessage);
        } catch (Exception e) {
            errorMessage = "删除异常：" + e.getClass().getSimpleName() + " - " + e.getMessage();
            Log.e(TAG, "deleteFile: " + errorMessage + " - " + fileName, e);
            e.printStackTrace();
            return new DeleteResult(false, errorMessage);
        }
    }
    
    /**
     * 通知媒体扫描器文件已删除
     */
    private void notifyMediaStoreDeleted(File file) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                new String[]{file.getAbsolutePath()},
                null,
                null
            );
        } catch (Exception e) {
            Log.w(TAG, "notifyMediaStoreDeleted: 通知媒体扫描器失败", e);
        }
    }
    
    /**
     * 在主线程显示删除结果
     */
    private void showDeleteResult(String filePath, DeleteResult result) {
        mainHandler.post(() -> {
            String fileName = new File(filePath).getName();
            String message;
            
            if (result.success) {
                message = "已删除: " + fileName;
            } else {
                message = "删除失败: " + fileName + "\n原因: " + result.errorMessage;
            }
            
            Toast.makeText(context, message, 
                result.success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        });
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
