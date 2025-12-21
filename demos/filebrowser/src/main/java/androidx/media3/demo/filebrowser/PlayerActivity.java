package androidx.media3.demo.filebrowser;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";
    
    private PlayerView playerView;
    private Button deleteButton;
    private ExoPlayer player;
    private ArrayList<String> fileList;
    private int currentIndex;
    private String currentFilePath;
    private ExecutorService deleteExecutor;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        // 初始化删除线程池和主线程Handler
        deleteExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        playerView = findViewById(R.id.player_view);
        deleteButton = findViewById(R.id.delete_button);
        
        Intent intent = getIntent();
        currentFilePath = intent.getStringExtra("file_path");
        fileList = intent.getStringArrayListExtra("file_list");
        currentIndex = intent.getIntExtra("current_index", 0);

        if (fileList == null) {
            fileList = new ArrayList<>();
            if (currentFilePath != null) {
                fileList.add(currentFilePath);
            }
        }

        initializePlayer();

        deleteButton.setOnClickListener(v -> deleteCurrentFile());
        // 删除重复的初始化代码
    }

    private void initializePlayer() {
        if (currentFilePath == null || fileList.isEmpty()) {
            finish();
            return;
        }

        // 如果播放器已存在，先释放
        if (player != null) {
            player.release();
            player = null;
        }

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // 将所有视频文件转换为 MediaItem 列表
        List<MediaItem> mediaItems = new ArrayList<>();
        for (String filePath : fileList) {
            File file = new File(filePath);
            if (file.exists()) {
                mediaItems.add(MediaItem.fromUri(Uri.fromFile(file)));
            }
        }

        if (mediaItems.isEmpty()) {
            finish();
            return;
        }

        // 使用 setMediaItems 设置播放列表，这样"下一个"按钮才会可用
        // 从当前索引开始播放
        player.setMediaItems(mediaItems, currentIndex, 0);
        player.prepare();
        player.play();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                // 播放错误时播放下一个
                playNext();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // 当播放结束时，自动播放下一个
                if (playbackState == Player.STATE_ENDED) {
                    // 播放结束，切换到下一个视频
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem();
                    } else {
                        // 没有下一个视频，关闭播放器
                        finish();
                    }
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                // 当切换到新的视频时，更新当前索引和文件路径
                if (player.getCurrentMediaItemIndex() >= 0 && 
                    player.getCurrentMediaItemIndex() < fileList.size()) {
                    currentIndex = player.getCurrentMediaItemIndex();
                    currentFilePath = fileList.get(currentIndex);
                }
            }
        });
    }

    private void deleteCurrentFile() {
        if (currentFilePath == null) {
            Log.w(TAG, "deleteCurrentFile: currentFilePath is null");
            Toast.makeText(this, "文件路径为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 检查是否有删除权限
        if (!PermissionManager.hasManageStoragePermission()) {
            Log.w(TAG, "deleteCurrentFile: 没有 MANAGE_EXTERNAL_STORAGE 权限");
            Toast.makeText(this, 
                "需要\"管理所有文件\"权限才能删除文件", 
                Toast.LENGTH_LONG).show();
            PermissionManager.requestManageStoragePermission(this);
            return;
        }
        
        File file = new File(currentFilePath);
        String fileName = file.getName();
        
        Log.d(TAG, "deleteCurrentFile: 准备删除文件 - " + currentFilePath);
        Log.d(TAG, "deleteCurrentFile: 文件存在 - " + file.exists());
        Log.d(TAG, "deleteCurrentFile: 文件可读 - " + file.canRead());
        Log.d(TAG, "deleteCurrentFile: 文件可写 - " + file.canWrite());
        Log.d(TAG, "deleteCurrentFile: 文件大小 - " + file.length() + " bytes");
        
        // 保存要删除的文件路径
        String fileToDelete = currentFilePath;
        
        // ⚠️ 关键：先释放播放器对文件的占用
        if (player != null) {
            Log.d(TAG, "deleteCurrentFile: 释放播放器资源");
            player.stop();
            player.clearMediaItems();
        }
        
        // 先切换到下一个视频或关闭播放器
        if (player != null && fileList != null && !fileList.isEmpty()) {
            // 从列表中移除当前文件（但先不删除实际文件）
            int deletedIndex = currentIndex;
            fileList.remove(deletedIndex);
            
            // 检查是否有下一个视频
            if (fileList.isEmpty()) {
                // 列表为空，关闭播放器
                Log.d(TAG, "deleteCurrentFile: 列表为空，关闭播放器");
                finish();
            } else {
                // 调整当前索引 - 如果删除的是最后一个，索引需要减1
                if (currentIndex >= fileList.size()) {
                    currentIndex = fileList.size() - 1;
                }
                
                // 重新构建播放列表
                List<MediaItem> mediaItems = new ArrayList<>();
                for (String filePath : fileList) {
                    File f = new File(filePath);
                    if (f.exists()) {
                        mediaItems.add(MediaItem.fromUri(Uri.fromFile(f)));
                    }
                }
                
                if (mediaItems.isEmpty()) {
                    Log.d(TAG, "deleteCurrentFile: 没有可播放的文件，关闭播放器");
                    finish();
                } else {
                    // 更新播放列表，从当前索引开始播放
                    int playIndex = Math.min(currentIndex, mediaItems.size() - 1);
                    playIndex = Math.max(0, playIndex);
                    
                    currentIndex = playIndex;
                    currentFilePath = fileList.get(currentIndex);
                    
                    player.setMediaItems(mediaItems, playIndex, 0);
                    player.prepare();
                    player.play();
                    
                    Log.d(TAG, "deleteCurrentFile: 切换到下一个视频 - " + currentFilePath + ", 索引: " + currentIndex);
                }
            }
        } else {
            // 播放器为空或列表为空，直接关闭
            finish();
        }
        
        // 异步删除文件
        deleteFileAsync(fileToDelete, fileName);
    }
    
    /**
     * 异步删除文件
     */
    private void deleteFileAsync(String filePath, String fileName) {
        // 检查executor是否有效
        if (deleteExecutor == null) {
            Log.e(TAG, "deleteFileAsync: deleteExecutor 为 null");
            // 重新创建线程池
            deleteExecutor = Executors.newSingleThreadExecutor();
            Log.e(TAG, "deleteFileAsync: 重新创建了 deleteExecutor");
        } 
        if (deleteExecutor.isShutdown() || deleteExecutor.isTerminated()) {
            Log.e(TAG, "deleteFileAsync: 删除线程池已关闭或终止");
            // 重新创建线程池
            deleteExecutor = Executors.newSingleThreadExecutor();
            Log.e(TAG, "deleteFileAsync: 重新创建了 deleteExecutor");
        }

        // 异步执行删除任务
        try {
            Log.d(TAG, "deleteFileAsync: 准备执行删除任务");
            deleteExecutor.execute(() -> {
                Log.d(TAG, "deleteFileAsync: 进入删除任务执行体");
                final boolean[] deletedArray = {false};
                final String[] errorMessageArray = {null};
                
                try {
                    File file = new File(filePath);
                    
                    if (!file.exists()) {
                        Log.d(TAG, "deleteFileAsync: 文件不存在 - " + filePath);
                        mainHandler.post(() -> {
                            Toast.makeText(this, "文件不存在: " + fileName, Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                    
                    Log.d(TAG, "deleteFileAsync: 开始异步删除文件 - " + filePath);
                    Log.d(TAG, "deleteFileAsync: 文件可写性 - " + file.canWrite());
                    Log.d(TAG, "deleteFileAsync: 文件大小 - " + file.length() + " bytes");
                    Log.d(TAG, "deleteFileAsync: 文件路径 - " + file.getAbsolutePath());
                    Log.d(TAG, "deleteFileAsync: Android 版本 - " + Build.VERSION.SDK_INT);
                    
                    // 等待较长时间确保播放器完全释放文件资源
                    try {
                        Thread.sleep(1000);  // 增加到1秒
                    } catch (InterruptedException e) {
                        Log.w(TAG, "deleteFileAsync: 等待被中断", e);
                    }
                    
                    // 再次确认文件状态
                    Log.d(TAG, "deleteFileAsync: 等待后文件仍存在 - " + file.exists());
                    Log.d(TAG, "deleteFileAsync: 等待后文件可写 - " + file.canWrite());
                    
                    // Android 10+ (API 29+) 需要使用 MediaStore API 删除媒体文件
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        deletedArray[0] = deleteFileUsingMediaStore(file);
                        if (!deletedArray[0]) {
                            errorMessageArray[0] = "使用 MediaStore API 删除失败";
                            Log.e(TAG, "deleteFileAsync: MediaStore 删除失败，尝试直接删除");
                            // 即使 canWrite() 返回 false，也尝试直接删除
                            boolean directDeleted = file.delete();
                            if (directDeleted) {
                                Log.d(TAG, "deleteFileAsync: 直接删除成功");
                                deletedArray[0] = true;
                                errorMessageArray[0] = null;
                            } else {
                                // 检查文件是否还存在
                                if (!file.exists()) {
                                    Log.d(TAG, "deleteFileAsync: 文件已不存在（可能已被删除）");
                                    deletedArray[0] = true;
                                    errorMessageArray[0] = null;
                                } else {
                                    errorMessageArray[0] = "文件删除失败（权限不足或文件被占用）";
                                }
                            }
                        }
                    } else {
                        // Android 9 及以下，可以直接删除文件
                        deletedArray[0] = file.delete();
                        if (!deletedArray[0] && file.exists()) {
                            errorMessageArray[0] = "文件删除失败（权限不足或文件被占用）";
                        }
                    }
                } catch (SecurityException e) {
                    // 捕获权限异常
                    Log.e(TAG, "deleteFileAsync: 删除文件时发生权限异常", e);
                    errorMessageArray[0] = "删除文件时权限不足: " + e.getMessage();
                } catch (Exception e) {
                    // 捕获所有可能的异常，避免线程崩溃
                    Log.e(TAG, "deleteFileAsync: 异步删除文件发生异常", e);
                    errorMessageArray[0] = "删除文件时发生异常: " + e.getMessage();
                }
                
                // 更新UI，通知用户删除结果
                final boolean finalDeleted = deletedArray[0];
                final String finalErrorMessage = errorMessageArray[0];
                mainHandler.post(() -> {
                    if (finalDeleted) {
                        Toast.makeText(this, "文件删除成功: " + fileName, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "deleteFileAsync: 文件删除成功 - " + filePath);
                    } else {
                        Toast.makeText(this, "文件删除失败: " + fileName, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "deleteFileAsync: 文件删除失败 - " + filePath + ", 原因: " + finalErrorMessage);
                    }
                });
                
            });
            Log.d(TAG, "deleteFileAsync: 成功提交删除任务");
        } catch (Exception e) {
            // 捕获execute方法本身可能抛出的异常
            Log.e(TAG, "deleteFileAsync: 执行删除任务时发生异常", e);
            mainHandler.post(() -> {
                Toast.makeText(this, "执行删除任务失败: " + fileName, Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    /**
     * 使用 MediaStore API 删除文件（Android 10+）
     */
    private boolean deleteFileUsingMediaStore(File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        
        try {
            ContentResolver resolver = getContentResolver();
            Uri uri = getMediaStoreUri(file);
            
            if (uri != null) {
                Log.d(TAG, "deleteFileUsingMediaStore: 找到 MediaStore URI - " + uri);
                int deleted = resolver.delete(uri, null, null);
                Log.e(TAG, "deleteFileUsingMediaStore: MediaStore 删除结果 - " + deleted + ", URI: " + uri);
                
                    // 检查文件是否真的被删除了
                    if (deleted > 0) {
                        // 等待系统完成删除操作
                        try {
                            Thread.sleep(500);  // 增加等待时间
                        } catch (InterruptedException e) {
                            Log.w(TAG, "deleteFileUsingMediaStore: 等待被中断", e);
                        }
                        
                        // 检查文件是否已被删除
                        if (!file.exists()) {
                            Log.d(TAG, "deleteFileUsingMediaStore: MediaStore 删除成功");
                            return true;
                        }
                        
                        // 如果文件仍然存在，尝试直接删除
                        Log.w(TAG, "deleteFileUsingMediaStore: MediaStore删除后文件仍存在，尝试直接删除");
                        boolean directDeleted = file.delete();
                        if (directDeleted) {
                            Log.d(TAG, "deleteFileUsingMediaStore: 直接删除成功");
                            return true;
                        }
                        
                        // 再次检查文件是否存在
                        if (!file.exists()) {
                            Log.d(TAG, "deleteFileUsingMediaStore: 文件已不存在（可能已被删除）");
                            return true;
                        }
                        
                        Log.e(TAG, "deleteFileUsingMediaStore: 直接删除也失败，文件仍存在");
                        Log.e(TAG, "deleteFileUsingMediaStore: 文件可写性 - " + file.canWrite());
                        return false;
                    }
                
                // 如果MediaStore删除失败，尝试直接删除文件（即使 canWrite() 返回 false）
                if (deleted == 0) {
                    Log.e(TAG, "deleteFileUsingMediaStore: MediaStore删除返回0，可能是因为文件不在MediaStore中");
                    boolean directDeleted = file.delete();
                    if (directDeleted) {
                        Log.d(TAG, "deleteFileUsingMediaStore: 直接删除成功");
                        return true;
                    } else {
                        // 检查文件是否还存在
                        if (!file.exists()) {
                            Log.d(TAG, "deleteFileUsingMediaStore: 文件已不存在（可能已被删除）");
                            return true;
                        } else {
                            Log.e(TAG, "deleteFileUsingMediaStore: 直接删除也失败，文件仍存在");
                            Log.e(TAG, "deleteFileUsingMediaStore: 文件信息 - 大小: " + file.length() + " bytes");
                        }
                    }
                }
                
                return deleted > 0;
            } else {
                Log.e(TAG, "deleteFileUsingMediaStore: 无法获取 MediaStore URI，文件可能不在媒体库中");
                // 如果无法获取URI，尝试直接删除（即使 canWrite() 返回 false）
                boolean directDeleted = file.delete();
                if (directDeleted) {
                    Log.d(TAG, "deleteFileUsingMediaStore: 直接删除成功");
                    return true;
                } else {
                    // 检查文件是否还存在
                    if (!file.exists()) {
                        Log.d(TAG, "deleteFileUsingMediaStore: 文件已不存在（可能已被删除）");
                        return true;
                    } else {
                        Log.e(TAG, "deleteFileUsingMediaStore: 直接删除失败，文件仍存在");
                        Log.e(TAG, "deleteFileUsingMediaStore: 文件路径 - " + file.getAbsolutePath());
                    }
                }
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "deleteFileUsingMediaStore: 权限异常 - " + e.getClass().getSimpleName(), e);
            
            // Android 10+ 删除其他应用的文件可能需要用户授权
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                e instanceof android.app.RecoverableSecurityException) {
                
                android.app.RecoverableSecurityException recoverableException = 
                    (android.app.RecoverableSecurityException) e;
                
                // 在主线程请求用户授权
                mainHandler.post(() -> {
                    try {
                        Log.d(TAG, "deleteFileUsingMediaStore: 请求用户授权删除");
                        android.app.PendingIntent pendingIntent = 
                            recoverableException.getUserAction().getActionIntent();
                        startIntentSenderForResult(
                            pendingIntent.getIntentSender(),
                            1001,  // requestCode
                            null,
                            0,
                            0,
                            0,
                            null
                        );
                    } catch (Exception ex) {
                        Log.e(TAG, "deleteFileUsingMediaStore: 无法启动授权对话框", ex);
                        Toast.makeText(PlayerActivity.this, 
                            "删除失败：需要用户授权，请在设置中授予\"管理所有文件\"权限", 
                            Toast.LENGTH_LONG).show();
                    }
                });
                return false;
            }
            
            // 尝试直接删除
            boolean directDeleted = file.delete();
            if (directDeleted || !file.exists()) {
                Log.d(TAG, "deleteFileUsingMediaStore: 直接删除成功（权限异常后）");
                return true;
            }
            
            Log.e(TAG, "deleteFileUsingMediaStore: 权限异常后直接删除也失败", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "deleteFileUsingMediaStore: 发生异常", e);
            // 发生异常时，尝试直接删除
            boolean directDeleted = file.delete();
            if (directDeleted) {
                Log.d(TAG, "deleteFileUsingMediaStore: 异常后直接删除成功");
                return true;
            } else {
                if (!file.exists()) {
                    Log.d(TAG, "deleteFileUsingMediaStore: 异常后文件已不存在");
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * 获取文件的 MediaStore URI
     */
    private Uri getMediaStoreUri(File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        
        try {
            ContentResolver resolver = getContentResolver();
            String filePath = file.getAbsolutePath();
            String fileName = file.getName();
            long fileSize = file.length();
            
            Log.d(TAG, "getMediaStoreUri: 开始查询 - 文件路径: " + filePath);
            Log.d(TAG, "getMediaStoreUri: 文件名: " + fileName + ", 大小: " + fileSize);
            
            // 尝试查找视频文件
            // 在Android 10+中，DATA字段可能不可用，使用文件名和大小来匹配
            String[] projection = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, 
                MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE};
            String selection = null;
            String[] selectionArgs = null;
            
            // 优先尝试使用DATA字段（如果可用）
            Cursor cursor = null;
            try {
                selection = MediaStore.Video.Media.DATA + "=?";
                selectionArgs = new String[]{filePath};
                Log.d(TAG, "getMediaStoreUri: 尝试使用DATA字段查询视频文件");
                cursor = resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                );
                if (cursor != null) {
                    Log.d(TAG, "getMediaStoreUri: DATA字段查询结果数量 - " + cursor.getCount());
                }
            } catch (Exception e) {
                Log.w(TAG, "getMediaStoreUri: 使用DATA字段查询失败，尝试使用文件名", e);
            }
            
            // 如果使用DATA字段没找到，尝试使用文件名和大小
            if ((cursor == null || cursor.getCount() == 0) && fileSize > 0) {
                if (cursor != null) {
                    cursor.close();
                }
                try {
                    selection = MediaStore.Video.Media.DISPLAY_NAME + "=? AND " + 
                               MediaStore.Video.Media.SIZE + "=?";
                    selectionArgs = new String[]{fileName, String.valueOf(fileSize)};
                    Log.d(TAG, "getMediaStoreUri: 尝试使用文件名和大小查询视频文件");
                    cursor = resolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                    );
                    if (cursor != null) {
                        Log.d(TAG, "getMediaStoreUri: 文件名和大小查询结果数量 - " + cursor.getCount());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "getMediaStoreUri: 使用文件名和大小查询失败", e);
                }
            }
            
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                long id = cursor.getLong(idColumn);
                cursor.close();
                Uri uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                Log.d(TAG, "getMediaStoreUri: 找到视频 URI - " + uri + ", ID: " + id);
                return uri;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            Log.w(TAG, "getMediaStoreUri: 未找到视频文件，尝试查询音频文件");
            
            // 如果没找到，尝试音频文件
            try {
                selection = MediaStore.Audio.Media.DATA + "=?";
                selectionArgs = new String[]{filePath};
                Log.d(TAG, "getMediaStoreUri: 尝试使用DATA字段查询音频文件");
                cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                );
                if (cursor != null) {
                    Log.d(TAG, "getMediaStoreUri: 音频DATA字段查询结果数量 - " + cursor.getCount());
                }
            } catch (Exception e) {
                Log.w(TAG, "getMediaStoreUri: 音频DATA字段查询失败，尝试使用文件名", e);
            }
            
            if ((cursor == null || cursor.getCount() == 0) && fileSize > 0) {
                if (cursor != null) {
                    cursor.close();
                }
                try {
                    selection = MediaStore.Audio.Media.DISPLAY_NAME + "=? AND " + 
                               MediaStore.Audio.Media.SIZE + "=?";
                    selectionArgs = new String[]{fileName, String.valueOf(fileSize)};
                    Log.d(TAG, "getMediaStoreUri: 尝试使用文件名和大小查询音频文件");
                    cursor = resolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                    );
                    if (cursor != null) {
                        Log.d(TAG, "getMediaStoreUri: 音频文件名和大小查询结果数量 - " + cursor.getCount());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "getMediaStoreUri: 音频文件名和大小查询失败", e);
                }
            }
            
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                long id = cursor.getLong(idColumn);
                cursor.close();
                Uri uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                Log.d(TAG, "getMediaStoreUri: 找到音频 URI - " + uri + ", ID: " + id);
                return uri;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            Log.w(TAG, "getMediaStoreUri: 未找到 MediaStore URI - " + filePath);
            Log.w(TAG, "getMediaStoreUri: 文件可能不在 MediaStore 数据库中，将尝试直接删除");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "getMediaStoreUri: 获取 URI 异常", e);
            return null;
        }
    }

    private void playNext() {
        // 如果使用播放列表，直接使用 ExoPlayer 的导航功能
        if (player != null && player.getMediaItemCount() > 0) {
            // 从列表中移除当前文件
            if (currentIndex >= 0 && currentIndex < fileList.size()) {
                fileList.remove(currentIndex);
            }
            
            // 如果列表为空，关闭播放器
            if (fileList.isEmpty()) {
                finish();
                return;
            }
            
            // 重新构建播放列表
            List<MediaItem> mediaItems = new ArrayList<>();
            for (String filePath : fileList) {
                File file = new File(filePath);
                if (file.exists()) {
                    mediaItems.add(MediaItem.fromUri(Uri.fromFile(file)));
                }
            }
            
            if (mediaItems.isEmpty()) {
                finish();
                return;
            }
            
            // 调整索引
            if (currentIndex >= fileList.size()) {
                currentIndex = fileList.size() - 1;
            }
            if (currentIndex < 0) {
                currentIndex = 0;
            }
            
            // 更新播放列表
            player.setMediaItems(mediaItems, currentIndex, 0);
            player.prepare();
            player.play();
            
            // 更新当前文件路径
            if (currentIndex < fileList.size()) {
                currentFilePath = fileList.get(currentIndex);
            }
        } else {
            // 备用方案：重新初始化播放器
            if (currentIndex >= fileList.size()) {
                finish();
                return;
            }

            // 从当前索引开始查找存在的视频文件
            while (currentIndex < fileList.size()) {
                String nextPath = fileList.get(currentIndex);
                File nextFile = new File(nextPath);
                if (nextFile.exists()) {
                    currentFilePath = nextPath;
                    initializePlayer();
                    return;
                }
                // 文件不存在，继续查找下一个
                currentIndex++;
            }

            // 没有找到存在的视频文件，关闭播放器
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) {
            playerView.onResume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            playerView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (deleteExecutor != null) {
            deleteExecutor.shutdown();
        }
    }
}



