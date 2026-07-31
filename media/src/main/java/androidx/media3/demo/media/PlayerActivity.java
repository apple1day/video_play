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
import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Effect;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.ui.PlayerView;

import com.google.common.collect.ImmutableList;

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

    // 锁屏/触摸锁定
    private boolean isScreenLocked = false;
    private ImageButton lockButton;

    // 屏幕比例
    private static final int RESIZE_FIT    = 0; // 适应（黑边）
    private static final int RESIZE_FILL   = 1; // 填充（可能裁剪）
    private static final int RESIZE_ZOOM   = 2; // 拉伸
    private int currentResizeMode = RESIZE_FIT;
    private ImageButton resizeButton;

    // 长按快进相关
    private SeekPrefsManager seekPrefs;
    private boolean isLongPressing = false;
    private float longPressOriginalSpeed = 1.0f;
    private final android.os.Handler longPressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideSeekOverlayRunnable = () -> {
        if (seekOverlay != null) seekOverlay.setVisibility(View.GONE);
    };
    private static final long LONG_PRESS_TRIGGER_MS = 500; // 触发长按的阈值

    // 画质增强预设
    private VideoEnhancePreset currentEnhancePreset = VideoEnhancePreset.STANDARD;
    private static final String PREF_ENHANCE_PRESET = "video_enhance_preset";

    // 画中画 / 后台播放
    private boolean inPipMode = false;
    private boolean userLeaving = false;
    // 当播放列表来自"文件夹详情"时为 true，禁用 HomeFragment 的"加载更多"扩列表
    private boolean fromFolderPlaylist = false;
    // 后台线程池：用于异步扫描"伴生字幕"文件（同目录 listFiles() 属于同步磁盘 IO），
    // 避免在主线程对整个播放列表做同步 IO 扫描，导致打开播放器时卡顿。
    private final java.util.concurrent.ExecutorService subtitleScanExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updatePlaylistRunnable = this::updatePlaylistFromHomeFragment;
    private volatile boolean destroyed = false;
    private boolean serviceBound = false;

    @androidx.annotation.Nullable private PlaybackService boundService;
    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (destroyed) return;
            if (service instanceof PlaybackService.LocalBinder) {
                PlaybackService playbackService = ((PlaybackService.LocalBinder) service).getService();
                if (playbackService == null) return;
                boundService = playbackService;
                if (player != null) boundService.attachPlayer(player);
                pushServiceMetadata();
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            boundService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        destroyed = false;
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
        seekPrefs = SeekPrefsManager.getInstance(this);
        
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

                // 画质增强按钮
                android.widget.TextView enhanceButton = topControlsView.findViewById(R.id.enhance_button);
                if (enhanceButton != null) {
                    enhanceButton.setOnClickListener(v -> showEnhanceDialog(enhanceButton));
                    updateEnhanceButtonLabel(enhanceButton);
                }

                // 锁屏按钮
                lockButton = topControlsView.findViewById(R.id.lock_button);
                if (lockButton != null) {
                    lockButton.setOnClickListener(v -> toggleScreenLock());
                }

                // 屏幕比例切换按钮
                resizeButton = topControlsView.findViewById(R.id.resize_button);
                if (resizeButton != null) {
                    resizeButton.setOnClickListener(v -> cycleResizeMode());
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
    
    /** 判断当前是否为平板/大屏（sw >= 600dp） */
    private boolean isTablet() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    /** 判断当前屏幕方向是否横屏 */
    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // 单击：让 PlayerView 处理（显示/隐藏控制器）
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // 长按：进入快进模式（加速播放）
                startLongPressSeek();
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 双击快进/快退（按当前模式计算量）
                if (player == null) return false;
                int screenWidth = playerView.getWidth();
                float touchX = e.getX();
                long duration = player.getDuration();
                if (duration <= 0) return false;
                float swipeRatio = 0.2f; // 双击固定给一个中等量
                long seekMs = seekPrefs.calcSeekMs(duration, swipeRatio, touchX >= screenWidth / 2f);
                long currentPosition = player.getCurrentPosition();
                if (touchX < screenWidth / 2f) {
                    player.seekTo(Math.max(0, currentPosition - seekMs));
                    showSeekOverlay(SeekPrefsManager.buildOverlayText(seekMs, false));
                } else {
                    player.seekTo(Math.min(duration, currentPosition + seekMs));
                    showSeekOverlay(SeekPrefsManager.buildOverlayText(seekMs, true));
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e1 == null || e2 == null) return false;

                int screenWidth  = playerView.getWidth();
                int screenHeight = playerView.getHeight();
                float sx = e1.getX();
                float totalDeltaX = e2.getX() - e1.getX();
                float totalDeltaY = e1.getY() - e2.getY(); // 正值=向上

                float absDeltaX = Math.abs(totalDeltaX);
                float absDeltaY = Math.abs(totalDeltaY);

                // iPad大屏适配：手势识别阈值按屏幕密度动态调整
                float density = getResources().getDisplayMetrics().density;
                float scrollThreshold = isTablet() ? 40 * density : 30;
                float switchThreshold = isTablet() ? 120 * density : 80;

                // iPad上手势区域划分按实际像素，左1/3亮度，右1/3音量，中1/3切换视频
                float leftBoundary  = screenWidth * 0.33f;
                float rightBoundary = screenWidth * 0.67f;

                // 垂直滑动优先
                if (absDeltaY > absDeltaX * 1.5f && absDeltaY > scrollThreshold) {
                    if (sx < leftBoundary) {
                        adjustBrightness(totalDeltaY, screenHeight);
                        return true;
                    } else if (sx > rightBoundary) {
                        adjustVolume(totalDeltaY, screenHeight);
                        return true;
                    } else {
                        if (absDeltaY > switchThreshold) {
                            if (totalDeltaY > 0) {
                                if (player != null && player.hasNextMediaItem()) {
                                    player.seekToNextMediaItem();
                                    showSeekOverlay("下一个视频 ▼");
                                }
                            } else {
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
                if (e1 == null || e2 == null || player == null) return false;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // 水平快速滑动：根据滑动距离计算快进量
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 80) {
                    long duration = player.getDuration();
                    if (duration <= 0) return false;
                    // 以屏幕宽度归一化滑动距离（0–1），映射到快进区间
                    float swipeRatio = Math.min(1f, Math.abs(diffX) / playerView.getWidth());
                    boolean forward = diffX > 0;
                    long seekMs = seekPrefs.calcSeekMs(duration, swipeRatio, forward);
                    long pos = player.getCurrentPosition();
                    if (forward) {
                        player.seekTo(Math.min(duration, pos + seekMs));
                    } else {
                        player.seekTo(Math.max(0, pos - seekMs));
                    }
                    showSeekOverlay(SeekPrefsManager.buildOverlayText(seekMs, forward));
                    return true;
                }
                return false;
            }
        });

        // 为 PlayerView 添加触摸监听（同时处理长按释放）
        playerView.setOnTouchListener((v, event) -> {
            int action = event.getAction();

            // 锁屏模式：只允许触发解锁按钮（单击=显示lockButton，由GD处理）
            if (isScreenLocked) {
                if (action == MotionEvent.ACTION_UP) {
                    // 显示锁屏按钮提示
                    playerView.showController();
                }
                return true; // 消费所有事件，阻止穿透
            }

            // ACTION_DOWN：预约长按检测
            if (action == MotionEvent.ACTION_DOWN) {
                longPressHandler.postDelayed(() -> {
                    // 由 GestureDetector.onLongPress 触发，此处仅作备用
                }, LONG_PRESS_TRIGGER_MS);
            }

            // ACTION_UP / CANCEL：结束长按快进
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                stopLongPressSeek();
                isAdjustingVolume = false;
                isAdjustingBrightness = false;
            }

            boolean handled = gestureDetector.onTouchEvent(event);
            return handled;
        });
    }

    /** 进入长按快进：提升播放速度并显示提示。 */
    private void startLongPressSeek() {
        if (player == null || isLongPressing) return;
        isLongPressing = true;
        longPressOriginalSpeed = player.getPlaybackParameters().speed;
        float speed = seekPrefs.getLongPressSpeed();
        player.setPlaybackSpeed(speed);
        String label = (speed == (int) speed)
                ? ((int) speed) + "x"
                : speed + "x";
        showSeekOverlay("长按快进 " + label + " ▶▶");
    }

    /** 退出长按快进：恢复原速。 */
    private void stopLongPressSeek() {
        if (!isLongPressing) return;
        isLongPressing = false;
        longPressHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.setPlaybackSpeed(longPressOriginalSpeed);
        }
        if (seekOverlay != null) {
            seekOverlay.removeCallbacks(hideSeekOverlayRunnable);
            seekOverlay.setVisibility(View.GONE);
        }
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
    
    // 调节亮度：iPad不支持通过WindowManager调节系统亮度，仅在手机上生效
    // deltaY: 滑动距离（正值表示向上）
    // screenHeight: 屏幕高度
    private void adjustBrightness(float deltaY, int screenHeight) {
        // iPad（Android平板）亮度调节方式与手机相同，但需检查Settings权限
        if (isTablet() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.System.canWrite(this)) {
            // 无写入系统设置权限时跳过，避免SecurityException崩溃
            showSeekOverlay("亮度调节需要系统权限");
            return;
        }
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        
        // 滑动一个屏幕高度改变35%亮度
        float brightnessChange = (deltaY / screenHeight) * 0.35f;
        
        float currentBrightness = layoutParams.screenBrightness;
        if (currentBrightness < 0) {
            currentBrightness = 0.5f;
        }
        
        float newBrightness = currentBrightness + brightnessChange;
        newBrightness = Math.max(0.01f, Math.min(1.0f, newBrightness));
        
        if (Math.abs(newBrightness - layoutParams.screenBrightness) > 0.01f) {
            layoutParams.screenBrightness = newBrightness;
            try {
                getWindow().setAttributes(layoutParams);
                int brightnessPercent = (int) (newBrightness * 100);
                showSeekOverlay("亮度: " + brightnessPercent + "%");
                isAdjustingBrightness = true;
            } catch (Exception e) {
                android.util.Log.w(TAG, "adjustBrightness failed: " + e.getMessage());
            }
        }
    }
    
    private void showSeekOverlay(String text) {
        if (seekOverlay != null) {
            seekOverlay.setText(text);
            seekOverlay.setVisibility(View.VISIBLE);
            
            // 1.5秒后自动隐藏
            seekOverlay.removeCallbacks(hideSeekOverlayRunnable);
            seekOverlay.postDelayed(hideSeekOverlayRunnable, 1500);
        }
    }

    /** 弹出画质增强选择对话框。 */
    @androidx.annotation.OptIn(markerClass = UnstableApi.class)
    private void showEnhanceDialog(android.widget.TextView button) {
        VideoEnhancePreset[] presets = VideoEnhancePreset.values();
        String[] labels = new String[presets.length];
        for (int i = 0; i < presets.length; i++) labels[i] = presets[i].label;
        int currentIndex = currentEnhancePreset.ordinal();

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("画质")
            .setSingleChoiceItems(labels, currentIndex, (dialog, which) -> {
                currentEnhancePreset = presets[which];
                // 持久化用户选择
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putInt(PREF_ENHANCE_PRESET, which).apply();
                // 实时切换 Effect，无需重启 player
                if (player != null) {
                    player.setVideoEffects(currentEnhancePreset.buildEffectsForPipeline());
                }
                updateEnhanceButtonLabel(button);
                dialog.dismiss();
            })
            .show();
    }

    private void updateEnhanceButtonLabel(android.widget.TextView button) {
        if (button == null) return;
        button.setText(currentEnhancePreset == VideoEnhancePreset.STANDARD
                ? "画质" : currentEnhancePreset.label);
        button.setTextColor(currentEnhancePreset == VideoEnhancePreset.STANDARD
                ? 0xFFFFFFFF : 0xFFFFB300);
    }

    /** 弹出 字幕 / 音轨 / 倍速 选择面板。 */
    private void showTrackSheet() {
        if (player == null) return;
        TrackBottomSheet sheet = new TrackBottomSheet();
        sheet.attach(player);
        sheet.show(getSupportFragmentManager(), "track");
    }

    /** 锁屏：锁定时遮住所有控制，只保留解锁按钮。 */
    private void toggleScreenLock() {
        isScreenLocked = !isScreenLocked;
        View topControls = playerView.findViewById(R.id.top_controls);
        View bottomBar    = playerView.findViewById(R.id.exo_bottom_bar);
        if (topControls != null) {
            // 锁屏时隐藏除 lockButton 外的其他控件
            for (int i = 0; i < ((android.view.ViewGroup) topControls).getChildCount(); i++) {
                View child = ((android.view.ViewGroup) topControls).getChildAt(i);
                if (child != lockButton) {
                    child.setVisibility(isScreenLocked ? View.INVISIBLE : View.VISIBLE);
                }
            }
        }
        if (bottomBar != null) {
            bottomBar.setVisibility(isScreenLocked ? View.GONE : View.VISIBLE);
        }
        if (lockButton != null) {
            lockButton.setImageResource(isScreenLocked
                    ? android.R.drawable.ic_lock_lock
                    : android.R.drawable.ic_lock_idle_lock);
        }
        showSeekOverlay(isScreenLocked ? "已锁屏 — 单击解锁" : "已解锁");
    }

    /** 循环切换屏幕比例：适应 → 填充 → 拉伸 → 适应。 */
    private void cycleResizeMode() {
        currentResizeMode = (currentResizeMode + 1) % 3;
        String[] labels = {"适应（黑边）", "填充（裁剪）", "拉伸"};
        int[] modes = {
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        };
        playerView.setResizeMode(modes[currentResizeMode]);
        showSeekOverlay("比例: " + labels[currentResizeMode]);
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

    /** 判断当前要播放的视频文件是否位于 USB/SD 卡等可移动存储（非手机内部存储）。 */
    private boolean isCurrentSourceRemovableStorage() {
        if (currentFilePath == null) return false;
        try {
            File primaryStorage = android.os.Environment.getExternalStorageDirectory();
            if (primaryStorage == null) return false;
            String primaryPath = primaryStorage.getAbsolutePath();
            return !currentFilePath.startsWith(primaryPath);
        } catch (Exception e) {
            // 无法判断时保守地认为是内部存储，使用较小缓冲
            return false;
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

        // ── 解码器配置：优先硬解，fallback 软解 ──────────────────────────────
        // 使用 ToneMappingRenderersFactory 替代 DefaultRenderersFactory，
        // 内部用 ToneMappingVideoRenderer 覆盖 createPlaybackVideoGraphWrapper()，
        // 在 SDR 屏幕上强制启用 OpenGL HDR→SDR tone mapping，解决 HDR 视频发灰问题。
        ToneMappingRenderersFactory renderersFactory = new ToneMappingRenderersFactory(this);

        // ── LoadControl：按存储介质分级设置缓冲，平衡流畅度与内存 ─────────────────
        // 内部存储（/storage/emulated/0）读取稳定，用小缓冲即可流畅，避免高码率
        // 4K 视频占用过多内存；USB/SD 卡等可移动存储读取速度不稳定（尤其 USB2.0
        // 设备/劣质 SD 卡），缓冲太小遇到读盘抖动会更容易触发 rebuffer（卡顿转圈），
        // 因此对可移动存储适当放大缓冲区间以提升抗抖动能力。
        boolean isRemovableSource = isCurrentSourceRemovableStorage();
        int minBufferMs = isRemovableSource ? 15_000 : 5_000;
        int maxBufferMs = isRemovableSource ? 30_000 : 8_000;
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs      */ minBufferMs,
                /* maxBufferMs      */ maxBufferMs,
                /* bufferForPlaybackMs              */ 1_500,  // 首帧等待 1.5s
                /* bufferForPlaybackAfterRebufferMs */ 3_000   // 卡顿后恢复 3s
            )
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES) // 使用默认字节上限
            .setPrioritizeTimeOverSizeThresholds(true) // 时长优先，避免过度缓冲
            .build();

        // 提前读取 SharedPreferences，供后续轨道选择和画质预设共用
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        // ── 轨道选择：根据用户设置决定是否强制最高码率 ───────────────────────────
        // 默认关闭：ExoPlayer 按屏幕分辨率智能选轨，省内存，适合大多数场景。
        // 开启后：忽略屏幕尺寸限制，始终选择最高码率轨道（适合 4K/高码率视频）。
        boolean forceHighestBitrate = prefs.getBoolean(
                SettingsActivity.PREF_FORCE_HIGHEST_BITRATE, false);
        TrackSelectionParameters.Builder trackParamsBuilder =
                new TrackSelectionParameters.Builder(this);
        if (forceHighestBitrate) {
            trackParamsBuilder
                .clearViewportSizeConstraints()   // 禁用屏幕尺寸限制，不降分辨率
                .setForceHighestSupportedBitrate(true); // 强制选最高码率
        }
        TrackSelectionParameters trackParams = trackParamsBuilder.build();

        player = new ExoPlayer.Builder(this, renderersFactory)
            .setLoadControl(loadControl)
            .build();

        // 在 build() 之后设置轨道选择参数（Builder 不直接支持 setTrackSelectionParameters）
        player.setTrackSelectionParameters(trackParams);

        // ── 读取用户画质预设 ──────────────────────────────────────────────────
        currentEnhancePreset = VideoEnhancePreset.fromOrdinal(
                prefs.getInt(PREF_ENHANCE_PRESET, VideoEnhancePreset.STANDARD.ordinal()));

        // ── 启用 Effects Pipeline（HDR→SDR Tone Mapping + 画质增强）────────────
        // setVideoEffects() 必须传非空列表才能激活 OpenGL pipeline（含 ToneMapping）。
        // STANDARD 预设返回空列表，会跳过 pipeline 导致 ToneMappingVideoRenderer 的
        // setRequestOpenGlToneMapping 在 registerInput() 前未生效 → 黑屏。
        // 修复：始终传入 VideoEnhancePreset.buildEffectsForPipeline()，
        // 该方法在 STANDARD 时注入一个 No-op passthrough Effect 以保证 pipeline 被激活。
        player.setVideoEffects(currentEnhancePreset.buildEffectsForPipeline());

        // VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING 会放大填充，导致边缘裁剪
        // 使用 VIDEO_SCALING_MODE_SCALE_TO_FIT（默认），保持宽高比，不拉伸
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        player.setHandleAudioBecomingNoisy(true);
        // 先绑定 PlayerView，再 prepare，确保 Surface 在解码前已就绪，避免首帧黑屏
        playerView.setPlayer(player);

        // ── 构建播放列表：只对"当前要播放"的这一项做同步字幕扫描，其余项先用轻量
        // MediaItem（无磁盘 IO）占位，prepare() 后在后台线程异步补齐字幕，再通过
        // replaceMediaItem 无缝替换。避免播放列表很大时，主线程被逐个 listFiles()
        // 同步扫描字幕阻塞，导致打开播放器卡顿。
        List<MediaItem> mediaItems = new ArrayList<>();
        List<Integer> pendingSubtitleIndices = new ArrayList<>();
        for (int i = 0; i < fileList.size(); i++) {
            File file = new File(fileList.get(i));
            if (!file.exists()) continue;
            if (i == currentIndex) {
                // 当前播放项立即需要字幕，同步构建（仅 1 次 IO，可接受）
                mediaItems.add(SubtitleAutoLoader.buildItem(file));
            } else {
                // 其余项先占位，不做字幕扫描
                mediaItems.add(MediaItem.fromUri(Uri.fromFile(file)));
                pendingSubtitleIndices.add(mediaItems.size() - 1);
            }
        }

        if (mediaItems.isEmpty()) {
            finish();
            return;
        }

        // 使用 setMediaItems 设置播放列表，这样"下一个"按钮才会可用
        // 从当前索引开始播放；seekToDefaultPosition 由 savedPosition 逻辑覆盖
        player.setMediaItems(mediaItems, currentIndex, /* startPositionMs= */ 0);
        player.prepare();

        // 后台异步补齐其余视频的字幕配置，逐个 replaceMediaItem 更新（不影响当前播放）
        if (!pendingSubtitleIndices.isEmpty()) {
            List<String> filesSnapshot = new ArrayList<>(fileList);
            subtitleScanExecutor.execute(() -> {
                for (int idx : pendingSubtitleIndices) {
                    if (destroyed || Thread.currentThread().isInterrupted()) return;
                    if (idx >= filesSnapshot.size()) continue;
                    File file = new File(filesSnapshot.get(idx));
                    if (!file.exists()) continue;
                    MediaItem itemWithSubs = SubtitleAutoLoader.buildItem(file);
                    mainHandler.post(() -> {
                        if (destroyed || player == null || idx >= player.getMediaItemCount()) return;
                        try {
                            player.replaceMediaItem(idx, itemWithSubs);
                        } catch (Exception e) {
                            android.util.Log.w(TAG, "replaceMediaItem for subtitle failed: " + e.getMessage());
                        }
                    });
                }
            });
        }
        
        // 恢复播放位置（如果有历史记录）—— 必须在 prepare() 之后、play() 之前 seekTo
        long savedPosition = playHistoryManager.getPlayPosition(currentFilePath);
        if (savedPosition > 0) {
            player.seekTo(savedPosition);
        }
        
        // 恢复播放速度（在 seekTo 之后设置，避免速度被重置）
        PlaybackSpeedHelper.restorePlaybackSpeed(this, player);
        
        player.play();
        
        // 打开视频后标记为已观看（在 play() 之后，避免在 seekTo 期间触发不必要的 DB 写入）
        if (currentFilePath != null) {
            watchedManager.markAsWatched(currentFilePath);
        }

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
            
            // 延迟500ms后更新播放列表；复用可清理的 Handler，避免销毁后回调持有 Activity。
            mainHandler.removeCallbacks(updatePlaylistRunnable);
            mainHandler.postDelayed(updatePlaylistRunnable, 500);
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
        // 先占位加入 fileList，保证 currentIndex / checkAndRequestMoreVideos 等逻辑一致
        fileList.addAll(newVideos);

        // 后台线程异步扫描字幕并构建 MediaItem，避免在主线程做同步 IO（可能有多个新视频）
        subtitleScanExecutor.execute(() -> {
            List<MediaItem> newMediaItems = new ArrayList<>();
            for (String filePath : newVideos) {
                if (destroyed || Thread.currentThread().isInterrupted()) return;
                File file = new File(filePath);
                if (file.exists()) {
                    newMediaItems.add(SubtitleAutoLoader.buildItem(file));
                }
            }
            if (!newMediaItems.isEmpty()) {
                Context appCtx = getApplicationContext();
                mainHandler.post(() -> {
                    if (destroyed || player == null) return;
                    player.addMediaItems(newMediaItems);
                    android.util.Log.d(TAG, "成功添加 " + newMediaItems.size() + " 个新视频到播放列表");
                    Toast.makeText(appCtx, "已加载 " + newMediaItems.size() + " 个新视频", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void playNext() {
        if (player == null) {
            finish();
            return;
        }
        // 优先用 ExoPlayer 自带的列表跳转，无需重建 MediaItem 列表（性能更好）
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
            return;
        }
        // 没有下一个了，关闭播放器
        finish();
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
        serviceBound = bindService(new Intent(this, PlaybackService.class), serviceConn, Context.BIND_AUTO_CREATE);
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
        // 在 onStop 就保存进度，保证 Activity 被系统杀死时也能续播
        saveCurrentProgress();
        // 解绑服务；若播放器已暂停则停止前台
        if (serviceBound) {
            try { unbindService(serviceConn); } catch (Exception ignored) {}
            serviceBound = false;
        }
        boundService = null;
        if (player != null && !player.isPlaying()) {
            stopService(new Intent(this, PlaybackService.class));
        }
    }

    /** 保存当前播放进度并更新观看状态（可在 onStop/onDestroy 中调用）。 */
    private void saveCurrentProgress() {
        if (player == null || currentFilePath == null) return;
        try {
            long duration = player.getDuration();
            if (duration > 0) {
                long position = player.getCurrentPosition();
                playHistoryManager.savePlayPosition(currentFilePath, position, duration);
                watchedManager.checkAndMarkWatched(currentFilePath, position, duration);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error saving play position: " + e.getMessage());
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 横竖屏切换时重新应用沉浸式，避免iPad分屏/旋转后系统栏残留
        applyImmersive(true);
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
            // 根据实际视频宽高比设置PiP宽高比，iPad上避免比例错误导致崩溃
            Rational ratio = new Rational(16, 9);
            if (player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
                int vw = player.getVideoSize().width;
                int vh = player.getVideoSize().height;
                // PiP比例限制在 1:2.39 到 2.39:1 之间（系统要求）
                float aspect = (float) vw / vh;
                if (aspect > 2.39f) { vw = (int)(vh * 2.39f); }
                else if (aspect < 1f / 2.39f) { vh = (int)(vw * 2.39f); }
                ratio = new Rational(vw, vh);
            } else {
                int w = playerView != null ? playerView.getWidth() : 0;
                int h = playerView != null ? playerView.getHeight() : 0;
                if (w > 0 && h > 0) {
                    float aspect = (float) w / h;
                    if (aspect > 2.39f) { w = (int)(h * 2.39f); }
                    else if (aspect < 1f / 2.39f) { h = (int)(w * 2.39f); }
                    ratio = new Rational(w, h);
                }
            }
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
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        longPressHandler.removeCallbacksAndMessages(null);
        if (seekOverlay != null) seekOverlay.removeCallbacks(hideSeekOverlayRunnable);

        // onStop 已保存进度；onDestroy 再兜底一次（处理极端情况：onStop 未被调用）
        saveCurrentProgress();

        // 停止前台服务（避免幽灵通知）
        if (boundService != null) {
            try { boundService.detachPlayer(); } catch (Exception ignored) {}
        }
        if (serviceBound) {
            try { unbindService(serviceConn); } catch (Exception ignored) {}
            serviceBound = false;
        }
        boundService = null;
        stopService(new Intent(this, PlaybackService.class));

        // 关闭字幕异步扫描线程池，避免 Activity 销毁后残留任务继续引用 Activity
        subtitleScanExecutor.shutdownNow();

        // 释放播放器资源
        releasePlayer();
        super.onDestroy();
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
