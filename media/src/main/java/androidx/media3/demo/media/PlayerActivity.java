package androidx.media3.demo.media;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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
    private AudioManager audioManager;
    
    // 手势控制相关变量
    private float startX;
    private float startY;
    private int startVolume;
    private float startBrightness;
    private boolean isAdjustingVolume = false;
    private boolean isAdjustingBrightness = false;

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
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
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
        if (gestureHintOverlay == null) {
            return;
        }
        
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasShownHint = prefs.getBoolean("gesture_hint_shown", false);
        
        if (!hasShownHint) {
            // 首次显示提示
            gestureHintOverlay.setVisibility(View.VISIBLE);
            
            // 点击提示层任意位置关闭
            gestureHintOverlay.setOnClickListener(v -> {
                gestureHintOverlay.setVisibility(View.GONE);
                prefs.edit().putBoolean("gesture_hint_shown", true).apply();
            });
            
            View closeButton = gestureHintOverlay.findViewById(R.id.close_hint_button);
            if (closeButton != null) {
                closeButton.setOnClickListener(v -> {
                    gestureHintOverlay.setVisibility(View.GONE);
                    prefs.edit().putBoolean("gesture_hint_shown", true).apply();
                });
            }
        } else {
            // 已经显示过，确保隐藏
            gestureHintOverlay.setVisibility(View.GONE);
        }
    }
    
    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // 单击事件返回 false，让 PlayerView 处理（显示/隐藏控制器）
                return false;
            }
            
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
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                
                int screenWidth = playerView.getWidth();
                int screenHeight = playerView.getHeight();
                float startX = e1.getX();
                float totalDeltaX = e2.getX() - e1.getX(); // 水平总位移
                float totalDeltaY = e1.getY() - e2.getY(); // 垂直总位移（正值表示向上）
                
                // 根据滑动距离判断是水平还是垂直操作
                float absDeltaX = Math.abs(totalDeltaX);
                float absDeltaY = Math.abs(totalDeltaY);
                
                // 将屏幕分为三部分：左(0-33%)、中(33%-67%)、右(67%-100%)
                float leftBoundary = screenWidth * 0.33f;
                float rightBoundary = screenWidth * 0.67f;
                
                // 垂直滑动优先（上下滑动距离明显大于左右）
                if (absDeltaY > absDeltaX * 1.5f && absDeltaY > 30) {
                    if (startX < leftBoundary) {
                        // 左侧 - 调节亮度
                        adjustBrightness(totalDeltaY, screenHeight);
                        return true;
                    } else if (startX > rightBoundary) {
                        // 右侧 - 调节音量
                        adjustVolume(totalDeltaY, screenHeight);
                        return true;
                    } else {
                        // 中间区域 - 切换视频（需要更大的滑动距离）
                        if (absDeltaY > 80) {
                            if (totalDeltaY > 0) {
                                // 向上滑动 - 切换到下一个视频
                                if (player != null && player.hasNextMediaItem()) {
                                    player.seekToNextMediaItem();
                                    showSeekOverlay("下一个视频 ▼");
                                }
                            } else {
                                // 向下滑动 - 切换到上一个视频
                                if (player != null && player.hasPreviousMediaItem()) {
                                    player.seekToPreviousMediaItem();
                                    showSeekOverlay("上一个视频 ▲");
                                }
                            }
                            return true;
                        }
                    }
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
                
                // 水平快速滑动 - 快进/快退
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100) {
                    if (player != null) {
                        long currentPosition = player.getCurrentPosition();
                        long seekAmount = 15000; // 15秒
                        
                        if (diffX > 0) {
                            // 右滑 - 快进
                            long duration = player.getDuration();
                            long newPosition = Math.min(duration, currentPosition + seekAmount);
                            player.seekTo(newPosition);
                            showSeekOverlay("快进15秒 ▶▶");
                        } else {
                            // 左滑 - 快退
                            long newPosition = Math.max(0, currentPosition - seekAmount);
                            player.seekTo(newPosition);
                            showSeekOverlay("◀◀ 快退15秒");
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
                // 先让手势检测器处理
                boolean handled = gestureDetector.onTouchEvent(event);
                
                // 处理触摸结束事件，隐藏调节提示
                if (event.getAction() == MotionEvent.ACTION_UP || 
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                    isAdjustingVolume = false;
                    isAdjustingBrightness = false;
                }
                
                // 只有当手势被处理时才返回 true，否则返回 false 让 PlayerView 处理单击
                return handled;
            }
        });
    }
    
    // 调节音量
    // deltaY: 滑动距离（正值表示向上）
    // screenHeight: 屏幕高度
    private void adjustVolume(float deltaY, int screenHeight) {
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        
        // 滑动一个屏幕高度改变35%音量
        // 计算音量变化：(滑动距离 / 屏幕高度) * 35% * 最大音量
        float volumeChangePercent = (deltaY / screenHeight) * 0.35f;
        int volumeChange = Math.round(volumeChangePercent * maxVolume);
        int newVolume = Math.max(0, Math.min(maxVolume, currentVolume + volumeChange));
        
        if (newVolume != currentVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
            int volumePercent = (int) (newVolume * 100.0f / maxVolume);
            showSeekOverlay("音量: " + volumePercent + "%");
            isAdjustingVolume = true;
        }
    }
    
    // 调节亮度
    // deltaY: 滑动距离（正值表示向上）
    // screenHeight: 屏幕高度
    private void adjustBrightness(float deltaY, int screenHeight) {
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        
        // 滑动一个屏幕高度改变35%亮度
        // 计算亮度变化：(滑动距离 / 屏幕高度) * 0.35
        float brightnessChange = (deltaY / screenHeight) * 0.35f;
        
        // 如果当前亮度为自动（-1），则从0.5开始
        float currentBrightness = layoutParams.screenBrightness;
        if (currentBrightness < 0) {
            currentBrightness = 0.5f;
        }
        
        float newBrightness = currentBrightness + brightnessChange;
        
        // 限制在0.01到1.0之间
        newBrightness = Math.max(0.01f, Math.min(1.0f, newBrightness));
        
        if (Math.abs(newBrightness - layoutParams.screenBrightness) > 0.01f) {
            layoutParams.screenBrightness = newBrightness;
            getWindow().setAttributes(layoutParams);
            
            int brightnessPercent = (int) (newBrightness * 100);
            showSeekOverlay("亮度: " + brightnessPercent + "%");
            isAdjustingBrightness = true;
        }
    }
    
    private void showSeekOverlay(String text) {
        if (seekOverlay != null) {
            seekOverlay.setText(text);
            seekOverlay.setVisibility(View.VISIBLE);
            
            // 1.5秒后自动隐藏
            seekOverlay.removeCallbacks(null);
            seekOverlay.postDelayed(() -> {
                if (seekOverlay != null) {
                    seekOverlay.setVisibility(View.GONE);
                }
            }, 1500);
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
        if (isPending && player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
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
        // 音量键正常控制音量，不再切换播放速度
        return super.onKeyDown(keyCode, event);
    }
}
