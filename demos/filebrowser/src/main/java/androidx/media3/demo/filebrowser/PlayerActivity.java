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
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

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
        
        // 初始化删除线程池和主线程Handler
        deleteExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
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
        
        File file = new File(currentFilePath);
        String fileName = file.getName();
        
        Log.d(TAG, "deleteCurrentFile: 准备删除文件 - " + currentFilePath);
        
        // 保存要删除的文件路径
        String fileToDelete = currentFilePath;
        
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
        deleteExecutor.execute(() -> {
            File file = new File(filePath);
            
            if (!file.exists()) {
                Log.w(TAG, "deleteFileAsync: 文件不存在 - " + filePath);
                mainHandler.post(() -> {
                    Toast.makeText(this, "文件不存在: " + fileName, Toast.LENGTH_SHORT).show();
                });
                return;
            }
            
            Log.d(TAG, "deleteFileAsync: 开始异步删除文件 - " + filePath);
            
            // 等待一小段时间确保文件资源被释放
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Log.w(TAG, "deleteFileAsync: 等待被中断", e);
            }
            
            boolean deleted = false;
            String errorMessage = null;
            
            // Android 10+ (API 29+) 需要使用 MediaStore API 删除媒体文件
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                deleted = deleteFileUsingMediaStore(file);
                if (!deleted) {
                    errorMessage = "使用 MediaStore API 删除失败";
                    Log.e(TAG, "deleteFileAsync: MediaStore 删除失败，尝试直接删除");
                    // 即使 canWrite() 返回 false，也尝试直接删除
                    boolean directDeleted = file.delete();
                    if (directDeleted) {
                        Log.d(TAG, "deleteFileAsync: 直接删除成功");
                        deleted = true;
                        errorMessage = null;
                    } else {
                        // 检查文件是否还存在
                        if (!file.exists()) {
                            Log.d(TAG, "deleteFileAsync: 文件已不存在（可能已被删除）");
                            deleted = true;
                            errorMessage = null;
                        } else {
                            errorMessage = "文件删除失败（权限不足或文件被占用）";
                            Log.e(TAG, "deleteFileAsync: 直接删除也失败，文件仍存在");
                        }
                    }
                }
            } else {
                // Android 9 及以下使用传统方法
                if (file.canWrite()) {
                    deleted = file.delete();
                    if (!deleted) {
                        errorMessage = "文件删除失败（可能被占用）";
                        Log.e(TAG, "deleteFileAsync: 文件删除失败");
                    }
                } else {
                    errorMessage = "文件不可写（权限不足）";
                    Log.e(TAG, "deleteFileAsync: 文件不可写");
                }
            }
            
            // 在主线程显示结果
            final boolean finalDeleted = deleted;
            final String finalErrorMessage = errorMessage;
            mainHandler.post(() -> {
                if (finalDeleted) {
                    Log.d(TAG, "deleteFileAsync: 删除成功 - " + fileName);
                    Toast.makeText(this, "已删除: " + fileName, Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "deleteFileAsync: 删除失败 - " + fileName + ", 错误: " + finalErrorMessage);
                    Toast.makeText(this, "删除失败: " + fileName + "\n" + 
                        (finalErrorMessage != null ? finalErrorMessage : "未知错误"), 
                        Toast.LENGTH_LONG).show();
                }
            });
        });
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
                int deleted = resolver.delete(uri, null, null);
                Log.d(TAG, "deleteFileUsingMediaStore: MediaStore 删除结果 - " + deleted);
                
                // 检查文件是否真的被删除了
                if (deleted > 0) {
                    // 等待一下让系统完成删除操作
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Log.w(TAG, "deleteFileUsingMediaStore: 等待被中断", e);
                    }
                    
                    // 如果文件仍然存在，尝试直接删除
                    if (file.exists()) {
                        Log.w(TAG, "deleteFileUsingMediaStore: MediaStore删除后文件仍存在，尝试直接删除");
                        // 即使 canWrite() 返回 false，也尝试直接删除
                        boolean directDeleted = file.delete();
                        if (directDeleted) {
                            Log.d(TAG, "deleteFileUsingMediaStore: 直接删除成功");
                            return true;
                        } else {
                            // 再次检查文件是否存在
                            if (!file.exists()) {
                                Log.d(TAG, "deleteFileUsingMediaStore: 文件已不存在（可能已被删除）");
                                return true;
                            }
                            Log.w(TAG, "deleteFileUsingMediaStore: 直接删除也失败");
                        }
                    } else {
                        Log.d(TAG, "deleteFileUsingMediaStore: 文件已成功删除");
                        return true;
                    }
                }
                
                // 如果MediaStore删除失败，尝试直接删除文件（即使 canWrite() 返回 false）
                if (deleted == 0) {
                    Log.w(TAG, "deleteFileUsingMediaStore: MediaStore删除返回0，尝试直接删除");
                    boolean directDeleted = file.delete();
                    if (directDeleted) {
                        Log.d(TAG, "deleteFileUsingMediaStore: 直接删除成功");
                        return true;
                    } else {
                        // 检查文件是否还存在
                        if (!file.exists()) {
                            Log.d(TAG, "deleteFileUsingMediaStore: 文件已不存在（可能已被删除）");
                            return true;
                        }
                    }
                }
                
                return deleted > 0;
            } else {
                Log.w(TAG, "deleteFileUsingMediaStore: 无法获取 MediaStore URI，尝试直接删除");
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
                    }
                }
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "deleteFileUsingMediaStore: 权限异常", e);
            // 权限不足时，尝试直接删除（即使 canWrite() 返回 false）
            boolean directDeleted = file.delete();
            if (directDeleted) {
                Log.d(TAG, "deleteFileUsingMediaStore: 直接删除成功（权限异常后）");
                return true;
            } else {
                // 检查文件是否还存在
                if (!file.exists()) {
                    Log.d(TAG, "deleteFileUsingMediaStore: 文件已不存在（权限异常后）");
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "deleteFileUsingMediaStore: 删除异常", e);
            // 异常时，尝试直接删除（即使 canWrite() 返回 false）
            boolean directDeleted = file.delete();
            if (directDeleted) {
                Log.d(TAG, "deleteFileUsingMediaStore: 直接删除成功（异常后）");
                return true;
            } else {
                // 检查文件是否还存在
                if (!file.exists()) {
                    Log.d(TAG, "deleteFileUsingMediaStore: 文件已不存在（异常后）");
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

