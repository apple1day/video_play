package androidx.media3.demo.media.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;
import androidx.media3.demo.media.FileItem;
import androidx.media3.demo.media.FileListCacheManager;
import androidx.media3.demo.media.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 视频列表仓库。
 *
 * 把"扫描所有存储 / 用磁盘缓存兜底 / 增量回调 / 完成后保存缓存"这一套
 * 从 HomeFragment 抽出来。HomeFragment 现在只需要订阅 ScanCallback，UI 自己不再操心 IO。
 *
 * 不依赖任何 Android Framework UI 类，便于将来单元测试。
 */
public class VideoRepository {

    /** 加载阶段，便于 ViewModel/Fragment 渲染对应的占位 UI。 */
    public enum LoadPhase { IDLE, LOADING_CACHE, SCANNING, COMPLETED }

    public interface ScanCallback {
        /** 阶段变化（始终在主线程）。 */
        @MainThread void onPhaseChanged(LoadPhase phase);
        /** 扫描中每来一批新文件（主线程）。 */
        @MainThread void onBatchLoaded(List<FileItem> batch);
        /** 扫描完成，allFiles = 全部文件，videoFiles = 已按时间倒序的视频子集（主线程）。 */
        @MainThread void onScanComplete(List<FileItem> allFiles, List<FileItem> videoFiles);
    }

    private static VideoRepository INSTANCE;

    public static synchronized VideoRepository get(Context ctx) {
        if (INSTANCE == null) INSTANCE = new VideoRepository(ctx.getApplicationContext());
        return INSTANCE;
    }

    private final Context appCtx;
    private final FileListCacheManager cacheManager;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private VideoRepository(Context appCtx) {
        this.appCtx = appCtx;
        this.cacheManager = FileListCacheManager.getInstance(appCtx);
    }

    /**
     * 加载视频文件列表。
     * 流程：
     *   1) 若 forceRefresh=false 且磁盘缓存有效 → 直接走缓存，回调 onScanComplete 即可结束；
     *   2) 否则扫描所有存储设备（内部 + 所有外接），增量回调 onBatchLoaded；
     *   3) 扫描完成保存缓存，回调 onScanComplete。
     */
    public void loadVideos(FileItem.FileType filterType, boolean forceRefresh, ScanCallback cb) {
        notifyPhase(cb, LoadPhase.LOADING_CACHE);
        io.execute(() -> {
            // 1) 缓存命中
            if (!forceRefresh) {
                List<FileItem> cached = cacheManager.loadFileList(true);
                if (cached != null && !cached.isEmpty()) {
                    List<FileItem> videos = filterVideosSortedByTime(cached);
                    main.post(() -> {
                        if (cb != null) {
                            cb.onScanComplete(cached, videos);
                            cb.onPhaseChanged(LoadPhase.COMPLETED);
                        }
                    });
                    return;
                }
            }
            // 2) 缓存不命中或强制刷新 → 扫盘
            main.post(() -> { if (cb != null) cb.onPhaseChanged(LoadPhase.SCANNING); });
            List<File> targets = collectAllStorageDirectories();
            final List<FileItem> allScanned = new ArrayList<>();
            final List<FileItem> videos = new ArrayList<>();

            FileUtils.scanAllFilesAsync(targets, filterType, new FileUtils.ScanCallback() {
                @Override public void onBatchLoaded(List<FileItem> batch) {
                    main.post(() -> {
                        synchronized (allScanned) {
                            allScanned.addAll(batch);
                            List<FileItem> newVideos = new ArrayList<>();
                            for (FileItem fi : batch) {
                                if (fi.getFileType() == FileItem.FileType.VIDEO) newVideos.add(fi);
                            }
                            videos.addAll(newVideos);
                            if (cb != null && !newVideos.isEmpty()) cb.onBatchLoaded(newVideos);
                        }
                    });
                }

                @Override public void onScanComplete(List<FileItem> finalList) {
                    main.post(() -> {
                        // 完整排序一次
                        Collections.sort(videos, (a, b) -> Long.compare(b.getLastModified(), a.getLastModified()));
                        // 保存缓存（IO 线程异步执行，避免阻塞主线程）
                        io.execute(() -> cacheManager.saveFileList(new ArrayList<>(allScanned), true));
                        if (cb != null) {
                            cb.onScanComplete(new ArrayList<>(allScanned), new ArrayList<>(videos));
                            cb.onPhaseChanged(LoadPhase.COMPLETED);
                        }
                    });
                }
            });
        });
    }

    /** 主动失效缓存（下拉刷新前调用）。 */
    public void invalidateCache() {
        io.execute(cacheManager::clearCache);
    }

    /** 收集所有存储设备：主存储 + 所有非主存储（U盘 / SD 卡）。 */
    @WorkerThread
    private List<File> collectAllStorageDirectories() {
        List<File> all = FileUtils.getStorageDirectories(appCtx);
        File primary = android.os.Environment.getExternalStorageDirectory();
        List<File> out = new ArrayList<>();
        if (primary != null && primary.exists()) out.add(primary);
        for (File dir : all) {
            if (dir != null && dir.exists() && !out.contains(dir)) out.add(dir);
        }
        return out;
    }

    @WorkerThread
    private List<FileItem> filterVideosSortedByTime(List<FileItem> all) {
        List<FileItem> videos = new ArrayList<>();
        for (FileItem item : all) {
            if (item.getFileType() == FileItem.FileType.VIDEO) videos.add(item);
        }
        Collections.sort(videos, (a, b) -> Long.compare(b.getLastModified(), a.getLastModified()));
        return videos;
    }

    private void notifyPhase(ScanCallback cb, LoadPhase phase) {
        if (cb == null) return;
        main.post(() -> cb.onPhaseChanged(phase));
    }
}
