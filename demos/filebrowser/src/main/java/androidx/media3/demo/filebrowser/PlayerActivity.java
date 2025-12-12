package androidx.media3.demo.filebrowser;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";
    
    private PlayerView playerView;
    private Button deleteButton;
    private ExoPlayer player;
    private ArrayList<String> fileList;
    private int currentIndex;
    private String currentFilePath;

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
        
        Log.d(TAG, "deleteCurrentFile: 开始删除文件 - " + currentFilePath);
        Log.d(TAG, "deleteCurrentFile: 文件存在: " + file.exists());
        Log.d(TAG, "deleteCurrentFile: 文件可读: " + file.canRead());
        Log.d(TAG, "deleteCurrentFile: 文件可写: " + file.canWrite());
        Log.d(TAG, "deleteCurrentFile: Android版本: " + Build.VERSION.SDK_INT);
        
        if (!file.exists()) {
            Log.w(TAG, "deleteCurrentFile: 文件不存在 - " + currentFilePath);
            Toast.makeText(this, "文件不存在: " + fileName, Toast.LENGTH_SHORT).show();
            return;
        }
        
        boolean deleted = false;
        String errorMessage = null;
        
        // Android 10+ (API 29+) 需要使用 MediaStore API 删除媒体文件
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleted = deleteFileUsingMediaStore(file);
            if (!deleted) {
                errorMessage = "使用 MediaStore API 删除失败";
                Log.e(TAG, "deleteCurrentFile: MediaStore 删除失败");
                // 尝试使用传统方法作为备用
                if (file.canWrite()) {
                    deleted = file.delete();
                    if (deleted) {
                        Log.d(TAG, "deleteCurrentFile: 使用传统方法删除成功");
                    } else {
                        errorMessage = "文件删除失败（权限不足或文件被占用）";
                        Log.e(TAG, "deleteCurrentFile: 传统方法删除也失败");
                    }
                } else {
                    errorMessage = "文件不可写（权限不足）";
                    Log.e(TAG, "deleteCurrentFile: 文件不可写");
                }
            }
        } else {
            // Android 9 及以下使用传统方法
            if (file.canWrite()) {
                deleted = file.delete();
                if (!deleted) {
                    errorMessage = "文件删除失败（可能被占用）";
                    Log.e(TAG, "deleteCurrentFile: 文件删除失败");
                }
            } else {
                errorMessage = "文件不可写（权限不足）";
                Log.e(TAG, "deleteCurrentFile: 文件不可写");
            }
        }
        
        if (deleted) {
            Log.d(TAG, "deleteCurrentFile: 删除成功 - " + fileName);
            Toast.makeText(this, "已删除: " + fileName, Toast.LENGTH_SHORT).show();
            
            // 从列表中移除当前文件
            int deletedIndex = currentIndex;
            fileList.remove(deletedIndex);
            
            // 如果删除后列表为空，关闭播放器
            if (fileList.isEmpty()) {
                Log.d(TAG, "deleteCurrentFile: 列表为空，关闭播放器");
                finish();
                return;
            }
            
            // 调整当前索引
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
                return;
            }
            
            // 更新播放列表，从当前索引开始播放
            if (player != null) {
                player.setMediaItems(mediaItems, currentIndex, 0);
                player.prepare();
                player.play();
                
                // 更新当前文件路径
                if (currentIndex < fileList.size()) {
                    currentFilePath = fileList.get(currentIndex);
                    Log.d(TAG, "deleteCurrentFile: 更新当前文件路径 - " + currentFilePath);
                }
            }
        } else {
            Log.e(TAG, "deleteCurrentFile: 删除失败 - " + fileName + ", 错误: " + errorMessage);
            Toast.makeText(this, "删除失败: " + fileName + "\n" + (errorMessage != null ? errorMessage : "未知错误"), 
                Toast.LENGTH_LONG).show();
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
                int deleted = resolver.delete(uri, null, null);
                Log.d(TAG, "deleteFileUsingMediaStore: MediaStore 删除结果 - " + deleted);
                return deleted > 0;
            } else {
                Log.w(TAG, "deleteFileUsingMediaStore: 无法获取 MediaStore URI");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "deleteFileUsingMediaStore: 删除异常", e);
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
            
            // 尝试查找视频文件
            String[] projection = {MediaStore.Video.Media._ID};
            String selection = MediaStore.Video.Media.DATA + "=?";
            String[] selectionArgs = {filePath};
            
            Cursor cursor = resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                long id = cursor.getLong(idColumn);
                cursor.close();
                Uri uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                Log.d(TAG, "getMediaStoreUri: 找到视频 URI - " + uri);
                return uri;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            // 如果没找到，尝试音频文件
            cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                long id = cursor.getLong(idColumn);
                cursor.close();
                Uri uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                Log.d(TAG, "getMediaStoreUri: 找到音频 URI - " + uri);
                return uri;
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            Log.w(TAG, "getMediaStoreUri: 未找到 MediaStore URI - " + filePath);
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
    }
}

