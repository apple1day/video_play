package androidx.media3.demo.media;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import androidx.media3.demo.media.data.AppDatabase;
import androidx.media3.demo.media.data.PendingDeleteDao;
import androidx.media3.demo.media.data.PendingDeleteEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 管理待删除的视频文件。
 *
 * 存储已迁移到 Room（表：pending_delete，按 markedAt 升序对应原 LinkedHashSet 顺序）。
 * "最大数量"配置（max_pending_count）继续保留在 SharedPreferences 里，因为它是单个 int 值，
 * 没必要为它建一张表。
 */
public class PendingDeleteManager {
    private static final String TAG = "PendingDeleteManager";
    // 仅用于存放"最大数量"配置 + 一次性迁移标记
    private static final String PREFS_NAME = "pending_deletes";
    private static final String LEGACY_KEY = "pending_delete_files";
    private static final String KEY_MAX_COUNT = "max_pending_count";
    private static final String MIGRATE_PREF = "anxiong_migrate";
    private static final String MIGRATE_FLAG = "pending_delete_migrated_v1";
    private static final int DEFAULT_MAX_COUNT = 5;

    private static PendingDeleteManager instance;
    private final SharedPreferences prefs;
    private final Context context;
    private final ExecutorService deleteExecutor;
    private final Handler mainHandler;
    private final PendingDeleteDao dao;
    // 使用线程安全列表支持多个监听器，避免单例下相互覆盖
    private final CopyOnWriteArrayList<OnFileDeletedListener> listeners = new CopyOnWriteArrayList<>();

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
        this.dao = AppDatabase.get(this.context).pendingDeleteDao();
        migrateFromSharedPrefsIfNeeded();
    }

    public static synchronized PendingDeleteManager getInstance(Context context) {
        if (instance == null) {
            instance = new PendingDeleteManager(context);
        }
        return instance;
    }

    public void addOnFileDeletedListener(OnFileDeletedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeOnFileDeletedListener(OnFileDeletedListener listener) {
        listeners.remove(listener);
    }

    public boolean isPendingDelete(String filePath) {
        return filePath != null && dao.countByPath(filePath) > 0;
    }

    public void addPendingDelete(String filePath) {
        if (filePath == null) return;
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

                if (result.success) {
                    pendingFiles.remove(firstFile);
                } else {
                    Log.w(TAG, "addPendingDelete: 删除失败，保留待删除标记 - " + new File(firstFile).getName());
                    break;
                }

                if (!listeners.isEmpty()) {
                    final String file = firstFile;
                    final boolean success = result.success;
                    final String error = result.errorMessage;
                    mainHandler.post(() -> {
                        for (OnFileDeletedListener l : listeners) {
                            l.onFileDeleted(file, success, error);
                        }
                    });
                }

                showDeleteResult(firstFile, result);
            }

            savePendingFiles(pendingFiles);
            Log.d(TAG, "addPendingDelete: 当前待删除数量 = " + pendingFiles.size());
        }
    }

    public void removePendingDelete(String filePath) {
        if (filePath == null) return;
        dao.deleteByPath(filePath);
    }

    public boolean togglePendingDelete(String filePath) {
        if (isPendingDelete(filePath)) {
            removePendingDelete(filePath);
            return false;
        } else {
            addPendingDelete(filePath);
            return true;
        }
    }

    public List<String> getPendingDeleteList() {
        return new ArrayList<>(getPendingFiles());
    }

    public int getPendingCount() {
        return dao.count();
    }

    public void deleteAllPending() {
        List<String> pendingFiles = getPendingDeleteList();
        if (pendingFiles.isEmpty()) return;

        deleteExecutor.execute(() -> {
            int successCount = 0;
            int failCount = 0;
            List<String> failedFiles = new ArrayList<>();
            List<String> failedFilePaths = new ArrayList<>();

            for (String filePath : pendingFiles) {
                DeleteResult result = deleteFileSync(filePath);
                if (result.success) {
                    successCount++;
                    removePendingDelete(filePath);
                } else {
                    failCount++;
                    failedFiles.add(new File(filePath).getName() + ": " + result.errorMessage);
                    failedFilePaths.add(filePath);
                    Log.w(TAG, "deleteAllPending: 删除失败，保留待删除标记 - " + new File(filePath).getName());
                }
                Log.d(TAG, "deleteAllPending: " + new File(filePath).getName()
                    + ", 成功: " + result.success + ", 原因: " + result.errorMessage);
            }

            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;
            final List<String> finalFailedFiles = new ArrayList<>(failedFiles);

            mainHandler.post(() -> {
                for (OnFileDeletedListener l : listeners) {
                    l.onFilesDeleted(finalSuccessCount, finalFailCount, finalFailedFiles);
                }
                StringBuilder message = new StringBuilder();
                message.append("删除完成：成功 ").append(finalSuccessCount).append(" 个");
                if (finalFailCount > 0) {
                    message.append("，失败 ").append(finalFailCount).append(" 个");
                }
                Toast toast = Toast.makeText(context, message.toString(), Toast.LENGTH_LONG);
                toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 100);
                toast.show();

                if (!finalFailedFiles.isEmpty()) {
                    Log.e(TAG, "删除失败的文件（已保留待删除标记）：");
                    for (String failInfo : finalFailedFiles) {
                        Log.e(TAG, "  - " + failInfo);
                    }
                }
            });
        });
    }

    public void clearAllPending() {
        dao.clear();
    }

    public int getMaxPendingCount() {
        return prefs.getInt(KEY_MAX_COUNT, DEFAULT_MAX_COUNT);
    }

    public void setMaxPendingCount(int count) {
        if (count < 1) count = 1;
        prefs.edit().putInt(KEY_MAX_COUNT, count).apply();

        LinkedHashSet<String> pendingFiles = new LinkedHashSet<>(getPendingFiles());
        while (pendingFiles.size() > count && !pendingFiles.isEmpty()) {
            String firstFile = pendingFiles.iterator().next();
            DeleteResult result = deleteFileSync(firstFile);
            Log.d(TAG, "setMaxPendingCount: 超过新的最大值，删除文件 " + new File(firstFile).getName()
                + ", 结果: " + result.success + ", 原因: " + result.errorMessage);
            if (result.success) {
                pendingFiles.remove(firstFile);
            } else {
                Log.w(TAG, "setMaxPendingCount: 删除失败，保留待删除标记 - " + new File(firstFile).getName());
                break;
            }
            if (!listeners.isEmpty()) {
                final String file = firstFile;
                final boolean success = result.success;
                final String error = result.errorMessage;
                mainHandler.post(() -> {
                    for (OnFileDeletedListener l : listeners) {
                        l.onFileDeleted(file, success, error);
                    }
                });
            }
            showDeleteResult(firstFile, result);
        }
        savePendingFiles(pendingFiles);
        Log.d(TAG, "setMaxPendingCount: 设置为 " + count + ", 当前待删除数量 = " + pendingFiles.size());
    }

    /** 从 Room 取出有序集合（markedAt 升序，即添加先后顺序）。 */
    private Set<String> getPendingFiles() {
        return new LinkedHashSet<>(dao.listAllPaths());
    }

    /**
     * 把内存中的 LinkedHashSet 写回 Room。
     * 实现策略：算出新增/移除两个差集，分别 upsert / deleteByPath，
     * 保持 markedAt 时间戳不被全表覆盖（避免顺序错乱）。
     */
    private void savePendingFiles(Set<String> files) {
        Set<String> existing = new LinkedHashSet<>(dao.listAllPaths());
        long now = System.currentTimeMillis();
        for (String p : files) {
            if (!existing.contains(p)) {
                dao.upsert(new PendingDeleteEntity(p, now));
            }
        }
        for (String p : existing) {
            if (!files.contains(p)) {
                dao.deleteByPath(p);
            }
        }
    }

    /** 一次性把旧的 SharedPreferences 数据迁到 Room。 */
    private void migrateFromSharedPrefsIfNeeded() {
        SharedPreferences flag = context.getSharedPreferences(MIGRATE_PREF, Context.MODE_PRIVATE);
        if (flag.getBoolean(MIGRATE_FLAG, false)) return;
        Set<String> old = prefs.getStringSet(LEGACY_KEY, null);
        if (old != null && !old.isEmpty()) {
            long base = System.currentTimeMillis() - old.size();
            int i = 0;
            for (String p : old) {
                if (p != null) dao.upsert(new PendingDeleteEntity(p, base + (i++)));
            }
            Log.d(TAG, "Migrated " + old.size() + " pending-delete entries from SP to Room");
        }
        flag.edit().putBoolean(MIGRATE_FLAG, true).apply();
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
                    Log.w(TAG, "deleteFile: 文件删除后仍存在，尝试强制清理 - " + fileName);
                    
                    // 如果是0字节文件，尝试多次删除
                    if (file.length() == 0) {
                        boolean finalDeleted = false;
                        for (int i = 0; i < 3; i++) {
                            Log.d(TAG, "deleteFile: 尝试第 " + (i + 1) + " 次删除0字节文件 - " + fileName);
                            finalDeleted = file.delete();
                            
                            // 再次等待
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                // 忽略
                            }
                            
                            // 检查是否删除成功
                            if (!file.exists()) {
                                Log.d(TAG, "deleteFile: ✓ 0字节文件已成功删除 - " + fileName);
                                notifyMediaStoreDeleted(file);
                                return new DeleteResult(true, "删除成功");
                            }
                        }
                        
                        // 如果还是删不掉，尝试清空文件再删除
                        Log.w(TAG, "deleteFile: 0字节文件顽固，尝试清空后删除 - " + fileName);
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                            fos.getChannel().truncate(0);
                            fos.flush();
                        } catch (Exception e) {
                            Log.w(TAG, "deleteFile: 清空文件失败 - " + fileName, e);
                        }
                        
                        // 最后再尝试一次
                        finalDeleted = file.delete();
                        if (!file.exists()) {
                            Log.d(TAG, "deleteFile: ✓ 清空后成功删除0字节文件 - " + fileName);
                            notifyMediaStoreDeleted(file);
                            return new DeleteResult(true, "删除成功");
                        }
                        
                        // 仍然存在，放弃
                        errorMessage = "无法删除0字节残留文件（可能被系统锁定）";
                        Log.e(TAG, "deleteFile: ✗ " + errorMessage + " - " + fileName);
                        return new DeleteResult(false, errorMessage);
                    } else {
                        // 不是0字节文件，再尝试删除一次
                        deleted = file.delete();
                        
                        if (!file.exists()) {
                            Log.d(TAG, "deleteFile: ✓ 第二次删除成功 - " + fileName);
                            notifyMediaStoreDeleted(file);
                            return new DeleteResult(true, "删除成功");
                        }
                        
                        errorMessage = "删除失败（文件仍存在，大小: " + file.length() + " bytes）";
                        Log.e(TAG, "deleteFile: ✗ " + errorMessage + " - " + fileName);
                        return new DeleteResult(false, errorMessage);
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
     * 通知媒体库文件已被删除：从 MediaStore 中删除对应记录。
     * 使用 ContentResolver 删除而非 scanFile，避免扫描器重新创建0字节占位文件。
     */
    private void notifyMediaStoreDeleted(File file) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri collection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
            // 查找并删除 MediaStore 中对应的条目
            String selection = MediaStore.Video.Media.DATA + "=?";
            String[] selectionArgs = {file.getAbsolutePath()};
            int deleted = resolver.delete(collection, selection, selectionArgs);
            if (deleted > 0) {
                Log.d(TAG, "notifyMediaStoreDeleted: 已从MediaStore删除记录 - " + file.getName());
            } else {
                // MediaStore 中没有找到记录，也尝试图片库（兼容性处理）
                Uri imageCollection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                resolver.delete(imageCollection, MediaStore.Images.Media.DATA + "=?", selectionArgs);
                Log.d(TAG, "notifyMediaStoreDeleted: MediaStore中无对应视频记录 - " + file.getName());
            }
        } catch (Exception e) {
            Log.w(TAG, "notifyMediaStoreDeleted: 从MediaStore删除记录失败", e);
        }
    }
    
    /**
     * 在主线程显示删除结果（顶部弹窗）
     */
    private void showDeleteResult(String filePath, DeleteResult result) {
        mainHandler.post(() -> {
            String fileName = new File(filePath).getName();
            String message;
            
            if (result.success) {
                message = "✓ 已删除: " + fileName;
            } else {
                message = "✗ 删除失败: " + fileName + "\n" + result.errorMessage;
            }
            
            Toast toast = Toast.makeText(context, message, 
                result.success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
            toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 100);
            toast.show();
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
