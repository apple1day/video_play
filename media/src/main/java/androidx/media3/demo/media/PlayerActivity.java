package androidx.media3.demo.media;

import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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
    private ImageButton backButton;
    private ImageButton favoriteButton;
    private ImageButton pendingDeleteButton;
    private ExoPlayer player;
    private ArrayList<String> fileList;
    private int currentIndex;
    private String currentFilePath;
    private FavoriteManager favoriteManager;
    private PendingDeleteManager pendingDeleteManager;
    private PlayHistoryManager playHistoryManager;
    private WatchedManager watchedManager;
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

    // 画中画 / 后台播放
    private boolean inPipMode = false;
    private boolean userLeaving = false;
    // 当播放列表来自"文件夹详情"时为 true，禁用 HomeFragment 的"加载更多"扩列表
    private boolean fromFolderPlaylist = false;
    @androidx.annotation.Nullable private PlaybackService boundService;
    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof PlaybackService.LocalBinder) {
                boundService = ((PlaybackService.LocalBinder) service).getService();
                if (player != null) boundService.attachPlayer(player);
                pushServiceMetadata();
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) { boundService = null; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 启用边到边布局，便于沉浸式
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_player);
        applyImmersive(true);
        
        playerView = findViewById(R.id.player_view);
        seekOverlay = findViewById(R.id.seek_overlay);
        gestureHintOverlay = findViewById(R.id.gesture_hint_overlay);
        
        favoriteManager = FavoriteManager.getInstance(this);
        pendingDeleteManager = PendingDeleteManager.getInstance(this);
        playHistoryManager = PlayHistoryManager.getInstance(this);
        watchedManager = WatchedManager.getInstance(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        
        Intent intent = getIntent();
        currentFilePath = intent.getStringExtra("file_path");
        fileList = intent.getStringArrayListExtra("file_list");
        currentIndex = intent.getIntExtra("current_index", 0);
        // 是否来自"文件夹详情" —— 若是，播放列表必须严格保持文件夹内顺序，
        // 不能让 HomeFragment 的"上拉加载更多"回调把别处的视频拼到这里。
        fromFolderPlaylist = intent.getBooleanExtra("from_folder", false);

        if (fileList == null) {
            fileList = new ArrayList<>();
            if (currentFilePath != null) {
                fileList.add(currentFilePath);
            }
        }

        initializePlayer();
        
        // 延迟获取自定义控制器中的按钮
        playerView.post(() -> {
            // 获取顶部控制按钮
            View topControlsView = playerView.findViewById(R.id.top_controls);
            if (topControlsView != null) {
                // 返回按钮
                backButton = topControlsView.findViewById(R.id.back_button);
                if (backButton != null) {
                    backButton.setOnClickListener(v -> finish());
                }
                
                // 收藏按钮
                favoriteButton = topControlsView.findViewById(R.id.favorite_button);
                if (favoriteButton != null) {
                    favoriteButton.setOnClickListener(v -> toggleFavorite());
                }
                updateFavoriteButton();

                // 字幕 / 音轨 / 倍速 入口
                ImageButton trackButton = topControlsView.findViewById(R.id.track_button);
                if (trackButton != null) {
                    trackButton.setOnClickListener(v -> showTrackSheet());
                }
            }
            
            // 获取底部的删除按钮（现在在底部控制栏中）
            View bottomBarView = playerView.findViewById(R.id.exo_bottom_bar);
            if (bottomBarView != null) {
                pendingDeleteButton = bottomBarView.findViewById(R.id.pending_delete_button);
                if (pendingDeleteButton != null) {
                    pendingDeleteButton.setOnClickListener(v -> togglePendingDelete());
                }
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

    /** 弹出 字幕 / 音轨 / 倍速 选择面板。 */
    private void showTrackSheet() {
        if (player == null) return;
        TrackBottomSheet sheet = new TrackBottomSheet();
        sheet.attach(player);
        sheet.show(getSupportFragmentManager(), "track_sheet");
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
        player.setHandleAudioBecomingNoisy(true);
        playerView.setPlayer(player);

        // 将所有视频文件转换为 MediaItem 列表
        List<MediaItem> mediaItems = new ArrayList<>();
        for (String filePath : fileList) {
            File file = new File(filePath);
            if (file.exists()) {
                mediaItems.add(SubtitleAutoLoader.buildItem(file));
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
        
        // 打开视频时立即标记为已观看
        if (currentFilePath != null) {
            watchedManager.markAsWatched(currentFilePath);
        }
        
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
                android.util.Log.e(TAG, "Player error: " + error.getMessage());
                try {
                    playNext();
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error playing next: " + e.getMessage());
                    finish();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                try {
                    // 当播放结束时，自动播放下一个
                    if (playbackState == Player.STATE_ENDED) {
                        // 播放结束，切换到下一个视频
                        if (player != null && player.hasNextMediaItem()) {
                            player.seekToNextMediaItem();
                        } else {
                            // 没有下一个视频，关闭播放器
                            finish();
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error in onPlaybackStateChanged: " + e.getMessage());
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                try {
                    // 当切换到新的视频时，更新当前索引和文件路径
                    if (player != null && player.getCurrentMediaItemIndex() >= 0 && 
                        player.getCurrentMediaItemIndex() < fileList.size()) {
                        // 保存旧视频的播放位置，并检查是否已观看
                        if (currentFilePath != null) {
                            try {
                                long duration = player.getDuration();
                                if (duration > 0) {
                                    long position = player.getCurrentPosition();
                                    
                                    playHistoryManager.savePlayPosition(
                                        currentFilePath, 
                                        position,
                                        duration
                                    );
                                    
                                    // 检查并标记为已观看（播放超过80%自动标记）
                                    watchedManager.checkAndMarkWatched(currentFilePath, position, duration);
                                }
                            } catch (Exception e) {
                                android.util.Log.e(TAG, "Error saving old video position: " + e.getMessage());
                            }
                        }
                        
                        // 更新当前视频
                        currentIndex = player.getCurrentMediaItemIndex();
                        currentFilePath = fileList.get(currentIndex);
                        updateFavoriteButton();
                        updatePendingDeleteButton();
                        pushServiceMetadata();
                        
                        // 切换到新视频时立即标记为已观看
                        if (currentFilePath != null) {
                            watchedManager.markAsWatched(currentFilePath);
                        }
                        
                        // 恢复新视频的播放位置
                        long savedPosition = playHistoryManager.getPlayPosition(currentFilePath);
                        if (savedPosition > 0) {
                            player.seekTo(savedPosition);
                        }
                        
                        // 检查是否需要预加载更多视频
                        checkAndRequestMoreVideos();
                    }
                } catch (Exception e) {
                    android.util.Log.e(TAG, "Error in onMediaItemTransition: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 检查并请求加载更多视频
     * 当播放到接近列表末尾（倒数20个）时，自动请求加载
     */
    private void checkAndRequestMoreVideos() {
        if (fileList == null || currentIndex < 0) {
            return;
        }
        // 文件夹播放列表必须严格保持文件夹内的顺序与内容，不允许从 Home 主页"加载更多"扩列表。
        if (fromFolderPlaylist) {
            return;
        }
        
        // 获取 HomeFragment 的监听器
        HomeFragment.VideoListLoadListener listener = HomeFragment.getVideoListLoadListener();
        if (listener == null) {
            return;
        }
        
        // 当播放到倒数20个视频时，触发加载更多
        int remainingVideos = fileList.size() - currentIndex;
        if (remainingVideos <= 20) {
            android.util.Log.d(TAG, "接近列表末尾（剩余 " + remainingVideos + " 个），请求加载更多");
            
            // 请求加载更多
            listener.onRequestLoadMore();
            
            // 延迟500ms后更新播放列表
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                updatePlaylistFromHomeFragment();
            }, 500);
        }
    }
    
    /**
     * 从 HomeFragment 更新播放列表
     */
    private void updatePlaylistFromHomeFragment() {
        HomeFragment.VideoListLoadListener listener = HomeFragment.getVideoListLoadListener();
        if (listener == null || player == null) {
            return;
        }
        
        // 获取最新的视频列表
        ArrayList<String> newVideoList = listener.getCurrentVideoList();
        if (newVideoList == null || newVideoList.size() <= fileList.size()) {
            return; // 没有新数据
        }
        
        android.util.Log.d(TAG, "更新播放列表：从 " + fileList.size() + " 个增加到 " + newVideoList.size() + " 个");
        
        // 提取新增的视频
        List<String> newVideos = newVideoList.subList(fileList.size(), newVideoList.size());
        
        // 将新视频转换为 MediaItem
        List<MediaItem> newMediaItems = new ArrayList<>();
        for (String filePath : newVideos) {
            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                newMediaItems.add(SubtitleAutoLoader.buildItem(file));
            }
        }
        
        if (!newMediaItems.isEmpty()) {
            // 添加到播放列表
            player.addMediaItems(newMediaItems);
            fileList.addAll(newVideos);
            
            android.util.Log.d(TAG, "成功添加 " + newMediaItems.size() + " 个新视频到播放列表");
            Toast.makeText(this, "已加载 " + newMediaItems.size() + " 个新视频", Toast.LENGTH_SHORT).show();
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
                    mediaItems.add(SubtitleAutoLoader.buildItem(file));
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
        if (player != null && playerView != null) {
            playerView.onResume();
        }
        // 绑定后台服务（用于通知/锁屏控制）
        Intent svc = new Intent(this, PlaybackService.class)
                .putExtra(PlaybackService.EXTRA_FILE_PATH, currentFilePath);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        bindService(new Intent(this, PlaybackService.class), serviceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playerView != null) {
            playerView.onPause();
        }
        // 进入 PiP 或用户主动按 Home 时，不暂停（继续后台播放）；其他情况暂停
        if (!inPipMode && !userLeaving && player != null) {
            player.pause();
        }
        userLeaving = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && playerView != null) {
            playerView.onResume();
        }
        applyImmersive(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (playerView != null) {
            playerView.onPause();
        }
        // 解绑服务；若播放器已暂停则停止前台
        try { unbindService(serviceConn); } catch (Exception ignored) {}
        if (player != null && !player.isPlaying()) {
            stopService(new Intent(this, PlaybackService.class));
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        // 用户按 Home/最近任务键时，自动进入 PiP（仅在播放中）
        userLeaving = true;
        tryEnterPip();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        inPipMode = isInPictureInPictureMode;
        if (playerView != null) {
            // PiP 模式下隐藏播放控制
            playerView.setUseController(!isInPictureInPictureMode);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive(true);
    }

    private void tryEnterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (player == null || !player.isPlaying()) return;
        if (isInPictureInPictureMode()) return;
        try {
            Rational ratio = new Rational(16, 9);
            int w = playerView != null ? playerView.getWidth() : 0;
            int h = playerView != null ? playerView.getHeight() : 0;
            if (w > 0 && h > 0) ratio = new Rational(w, h);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(ratio).build();
            enterPictureInPictureMode(params);
        } catch (Exception e) {
            android.util.Log.w(TAG, "enter PiP failed: " + e.getMessage());
        }
    }

    private void applyImmersive(boolean immersive) {
        WindowInsetsControllerCompat ctl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (ctl == null) return;
        if (immersive) {
            ctl.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            ctl.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            ctl.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void pushServiceMetadata() {
        if (boundService != null && currentFilePath != null) {
            boundService.attachPlayer(player);
        }
        PlaybackService.requestUpdate(this,
                currentFilePath != null ? new java.io.File(currentFilePath).getName() : null,
                currentFilePath);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 保存播放位置，并检查是否已观看
        if (player != null && currentFilePath != null) {
            try {
                long duration = player.getDuration();
                if (duration > 0) {
                    long position = player.getCurrentPosition();

                    playHistoryManager.savePlayPosition(
                        currentFilePath,
                        position,
                        duration
                    );

                    // 检查并标记为已观看（播放超过80%自动标记）
                    watchedManager.checkAndMarkWatched(currentFilePath, position, duration);
                }
            } catch (Exception e) {
                // 捕获可能的异常，避免闪退
                android.util.Log.e(TAG, "Error saving play position: " + e.getMessage());
            }
        }

        // 停止前台服务（避免幽灵通知）
        if (boundService != null) {
            try { boundService.detachPlayer(); } catch (Exception ignored) {}
        }
        stopService(new Intent(this, PlaybackService.class));

        // 释放播放器资源
        releasePlayer();
    }
    
    private void releasePlayer() {
        if (player != null) {
            try {
                player.stop();
                player.release();
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error releasing player: " + e.getMessage());
            } finally {
                player = null;
            }
        }
        
        // 清理 PlayerView
        if (playerView != null) {
            playerView.setPlayer(null);
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 音量键正常控制音量，不再切换播放速度
        return super.onKeyDown(keyCode, event);
    }
}
