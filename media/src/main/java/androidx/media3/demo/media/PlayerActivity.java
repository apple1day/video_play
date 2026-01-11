package androidx.media3.demo.media;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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
    private ImageButton favoriteButton;
    private ImageButton pendingDeleteButton;
    private ExoPlayer player;
    private ArrayList<String> fileList;
    private int currentIndex;
    private String currentFilePath;
    private FavoriteManager favoriteManager;
    private PendingDeleteManager pendingDeleteManager;
    private PlayHistoryManager playHistoryManager;
    private GestureDetector gestureDetector;
    private TextView seekOverlay;
    private View gestureHintOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        playerView = findViewById(R.id.player_view);
        seekOverlay = findViewById(R.id.seek_overlay);
        gestureHintOverlay = findViewById(R.id.gesture_hint_overlay);
        
        favoriteManager = FavoriteManager.getInstance(this);
        pendingDeleteManager = PendingDeleteManager.getInstance(this);
        playHistoryManager = PlayHistoryManager.getInstance(this);
        
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
        
        // 延迟获取自定义控制器中的按钮
        playerView.post(() -> {
            View controllerView = playerView.findViewById(R.id.top_controls);
            if (controllerView != null) {
                favoriteButton = controllerView.findViewById(R.id.favorite_button);
                pendingDeleteButton = controllerView.findViewById(R.id.pending_delete_button);
                
                if (favoriteButton != null) {
                    favoriteButton.setOnClickListener(v -> toggleFavorite());
                }
                if (pendingDeleteButton != null) {
                    pendingDeleteButton.setOnClickListener(v -> togglePendingDelete());
                }
                
                updateFavoriteButton();
                updatePendingDeleteButton();
            }
        });
        
        // 设置手势检测
        setupGestureDetector();
        
        // 显示手势提示（首次使用）
        showGestureHintIfNeeded();
    }
    
    private void showGestureHintIfNeeded() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasShownHint = prefs.getBoolean("gesture_hint_shown", false);
        
        if (!hasShownHint && gestureHintOverlay != null) {
            gestureHintOverlay.setVisibility(View.VISIBLE);
            
            View closeButton = gestureHintOverlay.findViewById(R.id.close_hint_button);
            if (closeButton != null) {
                closeButton.setOnClickListener(v -> {
                    gestureHintOverlay.setVisibility(View.GONE);
                    prefs.edit().putBoolean("gesture_hint_shown", true).apply();
                });
            }
            
            // 5秒后自动关闭
            gestureHintOverlay.postDelayed(() -> {
                if (gestureHintOverlay != null) {
                    gestureHintOverlay.setVisibility(View.GONE);
                    prefs.edit().putBoolean("gesture_hint_shown", true).apply();
                }
            }, 5000);
        }
    }
    
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;
            
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 双击快进/快退
                int screenWidth = playerView.getWidth();
                float touchX = e.getX();
                
                if (player != null) {
                    long currentPosition = player.getCurrentPosition();
                    long seekAmount = 10000; // 10秒
                    
                    if (touchX < screenWidth / 2) {
                        // 左侧双击 - 快退10秒
                        long newPosition = Math.max(0, currentPosition - seekAmount);
                        player.seekTo(newPosition);
                        showSeekOverlay("◀◀ 快退10秒");
                    } else {
                        // 右侧双击 - 快进10秒
                        long duration = player.getDuration();
                        long newPosition = Math.min(duration, currentPosition + seekAmount);
                        player.seekTo(newPosition);
                        showSeekOverlay("快进10秒 ▶▶");
                    }
                    return true;
                }
                return false;
            }
            
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    // 垂直滑动
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            // 下滑 - 播放上一个视频
                            onSwipeDown();
                        } else {
                            // 上滑 - 播放下一个视频
                            onSwipeUp();
                        }
                        return true;
                    }
                } else {
                    // 水平滑动
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // 右滑 - 加入收藏
                            onSwipeRight();
                        } else {
                            // 左滑 - 加入待删除
                            onSwipeLeft();
                        }
                        return true;
                    }
                }
                
                return false;
            }
        });
        
        // 为PlayerView添加触摸监听
        playerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });
    }
    
    private void showSeekOverlay(String text) {
        if (seekOverlay != null) {
            seekOverlay.setText(text);
            seekOverlay.setVisibility(View.VISIBLE);
            
            // 1秒后自动隐藏
            seekOverlay.removeCallbacks(null);
            seekOverlay.postDelayed(() -> {
                if (seekOverlay != null) {
                    seekOverlay.setVisibility(View.GONE);
                }
            }, 1000);
        }
    }
    
    private void onSwipeUp() {
        // 上滑播放下一个视频
        playNextVideo();
    }
    
    private void onSwipeDown() {
        // 下滑播放上一个视频
        playPreviousVideo();
    }
    
    private void onSwipeLeft() {
        // 左滑加入待删除
        if (currentFilePath != null && !pendingDeleteManager.isPendingDelete(currentFilePath)) {
            pendingDeleteManager.addPendingDelete(currentFilePath);
            updatePendingDeleteButton();
            Toast.makeText(this, getString(R.string.marked_for_deletion), Toast.LENGTH_SHORT).show();
            
            // 标记为待删除后，自动播放下一个视频
            playNextVideo();
        }
    }
    
    private void onSwipeRight() {
        // 右滑加入收藏
        if (currentFilePath != null && !favoriteManager.isFavorite(currentFilePath)) {
            favoriteManager.addFavorite(currentFilePath);
            updateFavoriteButton();
            Toast.makeText(this, getString(R.string.favorited), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void playNextVideo() {
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
            Toast.makeText(this, R.string.next_video, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.last_video, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void playPreviousVideo() {
        if (player != null && player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
            Toast.makeText(this, R.string.previous_video, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.first_video, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void toggleFavorite() {
        if (currentFilePath == null) {
            return;
        }
        
        boolean isFavorited = favoriteManager.toggleFavorite(currentFilePath);
        updateFavoriteButton();
        
        String message = isFavorited 
            ? getString(R.string.favorited) 
            : getString(R.string.unfavorited);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void togglePendingDelete() {
        if (currentFilePath == null) {
            return;
        }
        
        boolean isPending = pendingDeleteManager.togglePendingDelete(currentFilePath);
        updatePendingDeleteButton();
        
        String message = isPending 
            ? getString(R.string.marked_for_deletion) 
            : getString(R.string.unmarked_for_deletion);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        
        // 如果标记为待删除，自动播放下一个视频
        if (isPending) {
            playNextVideo();
        }
    }
    
    private void updateFavoriteButton() {
        if (currentFilePath == null || favoriteButton == null) {
            return;
        }
        
        boolean isFavorited = favoriteManager.isFavorite(currentFilePath);
        if (isFavorited) {
            // 实心黄色星星
            favoriteButton.setImageResource(android.R.drawable.btn_star_big_on);
            favoriteButton.setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN);
        } else {
            // 空心星星
            favoriteButton.setImageResource(android.R.drawable.btn_star_big_off);
            favoriteButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
    }
    
    private void updatePendingDeleteButton() {
        if (currentFilePath == null || pendingDeleteButton == null) {
            return;
        }
        
        boolean isPending = pendingDeleteManager.isPendingDelete(currentFilePath);
        if (isPending) {
            // 红色删除图标
            pendingDeleteButton.setColorFilter(Color.parseColor("#FF0000"), PorterDuff.Mode.SRC_IN);
        } else {
            // 白色删除图标
            pendingDeleteButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
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
        
        // 恢复播放位置（如果有历史记录）
        long savedPosition = playHistoryManager.getPlayPosition(currentFilePath);
        if (savedPosition > 0) {
            player.seekTo(savedPosition);
        }
        
        // 恢复播放速度
        PlaybackSpeedHelper.restorePlaybackSpeed(this, player);
        
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
                    // 保存旧视频的播放位置
                    if (currentFilePath != null && player.getDuration() > 0) {
                        playHistoryManager.savePlayPosition(
                            currentFilePath, 
                            player.getCurrentPosition(),
                            player.getDuration()
                        );
                    }
                    
                    // 更新当前视频
                    currentIndex = player.getCurrentMediaItemIndex();
                    currentFilePath = fileList.get(currentIndex);
                    updateFavoriteButton();
                    updatePendingDeleteButton();
                    
                    // 恢复新视频的播放位置
                    long savedPosition = playHistoryManager.getPlayPosition(currentFilePath);
                    if (savedPosition > 0) {
                        player.seekTo(savedPosition);
                    }
                }
            }
        });
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
        
        // 保存播放位置
        if (player != null && currentFilePath != null && player.getDuration() > 0) {
            playHistoryManager.savePlayPosition(
                currentFilePath,
                player.getCurrentPosition(),
                player.getDuration()
            );
        }
        
        if (player != null) {
            player.release();
            player = null;
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 音量键循环切换播放速度
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && player != null) {
            PlaybackSpeedHelper.cycleSpeed(this, player);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
