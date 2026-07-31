package androidx.media3.demo.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * 后台播放 + 媒体通知服务。
 * 用兼容版 MediaSessionCompat 提供锁屏 / 通知栏 / 蓝牙耳机控制。
 *
 * 设计要点：
 *  - 不持有 ExoPlayer 生命周期：PlayerActivity 负责创建/释放，通过 attachPlayer/detach 把当前 player 交给本服务。
 *  - 服务用 startForegroundService 启动；attachPlayer 后立刻 startForeground，避免 5 秒 ANR。
 *  - 进程被回收时 stopSelf，避免幽灵通知。
 */
public class PlaybackService extends Service {

    private static final String TAG = "PlaybackService";
    public static final String CHANNEL_ID = "anxiong_playback";
    public static final int NOTIFICATION_ID = 4399;

    public static final String ACTION_ATTACH = "anxiong.attach";
    public static final String ACTION_DETACH = "anxiong.detach";
    public static final String ACTION_UPDATE = "anxiong.update";

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_FILE_PATH = "file_path";

    @Nullable private MediaSessionCompat mediaSession;
    @Nullable private ExoPlayer player;
    @Nullable private String title;
    @Nullable private String filePath;
    @Nullable private Player.Listener playerListener;
    private final LocalBinder binder = new LocalBinder(this);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 异步封面加载（避免在主线程做 IO），随 Service 生命周期创建和销毁，避免静态线程持有旧 Service。
    private final java.util.concurrent.ExecutorService coverExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean destroyed = false;
    @Nullable private Bitmap lastCover;
    @Nullable private String lastCoverPath;

    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
        createChannel();
        mediaSession = new MediaSessionCompat(this, "AnxiongPlayback");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { if (player != null) player.play(); }
            @Override public void onPause() { if (player != null) player.pause(); }
            @Override public void onSkipToNext() {
                if (player != null && player.hasNextMediaItem()) player.seekToNextMediaItem();
            }
            @Override public void onSkipToPrevious() {
                if (player != null && player.hasPreviousMediaItem()) player.seekToPreviousMediaItem();
            }
            @Override public void onSeekTo(long pos) { if (player != null) player.seekTo(pos); }
            @Override public void onStop() { stopService(); }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mediaSession == null) return START_NOT_STICKY;
        // 处理来自通知栏按钮的 MediaButton 事件
        MediaButtonReceiver.handleIntent(mediaSession, intent);

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_UPDATE.equals(action)) {
                title = intent.getStringExtra(EXTRA_TITLE);
                filePath = intent.getStringExtra(EXTRA_FILE_PATH);
                pushNotification();
            } else if (ACTION_DETACH.equals(action)) {
                stopService();
                return START_NOT_STICKY;
            } else {
                // 默认 ATTACH：先把占位通知推上来，避免 ANR
                title = intent.getStringExtra(EXTRA_TITLE);
                filePath = intent.getStringExtra(EXTRA_FILE_PATH);
                pushNotification();
            }
        } else {
            pushNotification();
        }
        return START_NOT_STICKY;
    }

    /** 由 PlayerActivity 调用，注入当前 ExoPlayer 实例。 */
    public void attachPlayer(ExoPlayer p) {
        if (player == p) return;
        detachPlayer();
        this.player = p;
        playerListener = new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) { pushNotification(); }
            @Override public void onPlaybackStateChanged(int state) { pushNotification(); }
        };
        p.addListener(playerListener);
        pushNotification();
    }

    public void detachPlayer() {
        if (player != null && playerListener != null) {
            try { player.removeListener(playerListener); } catch (Exception ignored) {}
        }
        player = null;
        playerListener = null;
    }

    private void stopService() {
        detachPlayer();
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
        stopSelf();
    }

    private void pushNotification() {
        if (destroyed || mediaSession == null || coverExecutor.isShutdown()) return;
        boolean isPlaying = player != null && player.isPlaying();
        long position = player != null ? player.getCurrentPosition() : 0L;
        long duration = player != null ? Math.max(0L, player.getDuration()) : 0L;

        // 同步 PlaybackState，让锁屏/系统媒体面板有正确的播放状态
        PlaybackStateCompat.Builder ps = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_SEEK_TO
                        | PlaybackStateCompat.ACTION_STOP)
                .setState(isPlaying ? PlaybackStateCompat.STATE_PLAYING
                                    : PlaybackStateCompat.STATE_PAUSED, position, 1.0f);
        mediaSession.setPlaybackState(ps.build());

        String rawTitle = title;
        if (rawTitle == null && filePath != null) rawTitle = new File(filePath).getName();
        final String displayTitle = (rawTitle != null) ? rawTitle : "正在播放";

        // 通知使用已缓存的封面（异步后台更新）
        final String currentPath = filePath;
        if (currentPath != null && !currentPath.equals(lastCoverPath)) {
            coverExecutor.execute(() -> {
                if (destroyed || Thread.currentThread().isInterrupted()) return;
                Bitmap bmp = null;
                try { bmp = ThumbnailLoader.extractThumbnail(currentPath); } catch (Exception ignored) {}
                final Bitmap finalBmp = bmp;
                mainHandler.post(() -> {
                    if (destroyed || !currentPath.equals(filePath)) return;
                    lastCover = finalBmp;
                    lastCoverPath = currentPath;
                    pushNotificationWithCover(displayTitle, finalBmp);
                });
            });
        } else {
            pushNotificationWithCover(displayTitle, lastCover);
        }
    }

    private void pushNotificationWithCover(String displayTitle, Bitmap cover) {
        if (destroyed || mediaSession == null) return;
        boolean isPlaying = player != null && player.isPlaying();
        long position = player != null ? player.getCurrentPosition() : 0L;
        long duration = player != null ? Math.max(0L, player.getDuration()) : 0L;

        // 同步 MediaSession 元数据（含封面）
        MediaMetadataCompat.Builder md = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, displayTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, displayTitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        if (cover != null) md.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover);
        mediaSession.setMetadata(md.build());

        Intent openIntent = new Intent(this, PlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, openIntent, piFlag);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(displayTitle)
                .setContentIntent(contentPi)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setColorized(false)
                .setLargeIcon(cover);

        nb.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_previous, "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));
        nb.addAction(new NotificationCompat.Action(
                isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                isPlaying ? "Pause" : "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        nb.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_media_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));
        nb.addAction(new NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel, "Stop",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_STOP)));

        androidx.media.app.NotificationCompat.MediaStyle style =
                new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                                this, PlaybackStateCompat.ACTION_STOP));
        nb.setStyle(style);

        Notification notification = nb.build();
        startForeground(NOTIFICATION_ID, notification);
        // 暂停后取消 ongoing 让通知可滑动清除
        if (!isPlaying) {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(Service.STOP_FOREGROUND_DETACH);
            } else {
                //noinspection deprecation
                stopForeground(false);
            }
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                        "播放控制", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("正在播放的视频通知");
                ch.setShowBadge(false);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }
        }
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        coverExecutor.shutdownNow();
        detachPlayer();
        binder.clear();
        lastCover = null;
        lastCoverPath = null;
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return binder; }

    /**
     * Binder 可能被 framework/native 临时持有；必须避免非静态内部类隐式强引用 Service。
     */
    public static final class LocalBinder extends android.os.Binder {
        private WeakReference<PlaybackService> serviceRef;

        LocalBinder(PlaybackService service) {
            serviceRef = new WeakReference<>(service);
        }

        @Nullable
        public PlaybackService getService() {
            return serviceRef != null ? serviceRef.get() : null;
        }

        void clear() {
            if (serviceRef != null) {
                serviceRef.clear();
                serviceRef = null;
            }
        }
    }

    /** 让外部静态触发通知刷新的便捷方法。 */
    public static void requestUpdate(Context ctx, @Nullable String title, @Nullable String filePath) {
        Context appCtx = ctx.getApplicationContext();
        Intent i = new Intent(appCtx, PlaybackService.class).setAction(ACTION_UPDATE);
        if (title != null) i.putExtra(EXTRA_TITLE, title);
        if (filePath != null) i.putExtra(EXTRA_FILE_PATH, filePath);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appCtx.startForegroundService(i);
        } else {
            appCtx.startService(i);
        }
    }
}
