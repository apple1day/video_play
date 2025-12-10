package com.anxiong.mv;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;

/**
 * 播放器活动，用于播放视频并提供删除功能
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_POSITION = "extra_video_position";
    public static final String EXTRA_VIDEO_LIST = "extra_video_list";
    
    private PlayerView playerView;
    private ExoPlayer player;
    private ImageButton deleteButton;
    
    private VideoItem[] videoList;
    private int currentPosition;
    private boolean isDeleting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // 获取视频列表和当前位置
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            videoList = (VideoItem[]) extras.getParcelableArray(EXTRA_VIDEO_LIST);
            currentPosition = extras.getInt(EXTRA_VIDEO_POSITION, 0);
        }
        
        if (videoList == null || videoList.length == 0) {
            Toast.makeText(this, "没有视频可播放", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        playerView = findViewById(R.id.player_view);
        deleteButton = findViewById(R.id.exo_delete_button);
        
        // 设置删除按钮点击监听器
        deleteButton.setOnClickListener(this::handleDeleteButtonClick);
        
        // 初始化播放器
        initializePlayer();
    }

    /**
     * 初始化ExoPlayer
     */
    private void initializePlayer() {
        // 创建音频属性
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        
        // 创建ExoPlayer实例
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .build();
        
        playerView.setPlayer(player);
        
        // 设置播放器事件监听器
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(@Player.State int playbackState) {
                if (playbackState == Player.STATE_ENDED && !isDeleting) {
                    // 播放结束，自动播放下一个视频
                    playNextVideo();
                }
            }
        });
        
        // 准备并播放当前视频
        playCurrentVideo();
    }

    /**
     * 播放当前视频
     */
    private void playCurrentVideo() {
        if (player == null || currentPosition < 0 || currentPosition >= videoList.length) {
            return;
        }
        
        VideoItem videoItem = videoList[currentPosition];
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoItem.getUri()));
        
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    /**
     * 播放下一个视频
     */
    private void playNextVideo() {
        if (currentPosition < videoList.length - 1) {
            currentPosition++;
            playCurrentVideo();
        } else {
            // 已经是最后一个视频
            Toast.makeText(this, "已经播放到最后一个视频", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 处理删除按钮点击事件
     */
    private void handleDeleteButtonClick(View view) {
        if (isDeleting || player == null || videoList == null || videoList.length == 0) {
            return;
        }
        
        isDeleting = true;
        
        // 停止当前播放
        player.stop();
        
        // 删除当前视频
        boolean deleteSuccess = deleteVideo(videoList[currentPosition]);
        
        if (deleteSuccess) {
            // 删除成功，更新视频列表
            VideoItem[] newVideoList = new VideoItem[videoList.length - 1];
            System.arraycopy(videoList, 0, newVideoList, 0, currentPosition);
            System.arraycopy(videoList, currentPosition + 1, newVideoList, currentPosition, videoList.length - currentPosition - 1);
            videoList = newVideoList;
            
            // 显示删除成功提示
            Toast.makeText(this, R.string.video_deleted_success, Toast.LENGTH_SHORT).show();
            
            if (videoList.length > 0) {
                // 如果还有视频，播放下一个
                if (currentPosition >= videoList.length) {
                    currentPosition = videoList.length - 1;
                }
                playCurrentVideo();
            } else {
                // 如果没有视频了，结束活动
                finish();
            }
        } else {
            // 删除失败，继续播放当前视频
            Toast.makeText(this, R.string.video_deleted_failed, Toast.LENGTH_SHORT).show();
            player.prepare();
            player.play();
        }
        
        isDeleting = false;
    }

    /**
     * 删除视频文件
     */
    private boolean deleteVideo(VideoItem videoItem) {
        ContentResolver contentResolver = getContentResolver();
        boolean deleteSuccess = false;
        
        try {
            // 先尝试删除MediaStore记录
            Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoItem.getId());
            int rowsDeleted = contentResolver.delete(contentUri, null, null);
            
            // 如果在Android Q以下，还需要直接删除文件
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                File file = new File(videoItem.getPath());
                deleteSuccess = file.delete() || rowsDeleted > 0;
            } else {
                deleteSuccess = rowsDeleted > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            deleteSuccess = false;
        }
        
        return deleteSuccess;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
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
