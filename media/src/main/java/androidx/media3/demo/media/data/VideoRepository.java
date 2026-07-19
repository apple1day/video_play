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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 视频列表仓库。
 *
 * 扫描策略（以"快速展示首屏"为核心）：
 *  1. 缓存命中 → 直接 onScanComplete，无排序（保持原始顺序）；
 *  2. 缓存未命中 → 扫盘，每扫到一批立即通过 onBatchLoaded 推出去（UI 追加展示），
 *     扫描完成后保存缓存，回调 onScanComplete（不再排序，保持文件系统顺序）。
 *
 * 不在 Repository 层做任何排序，排序交给 HomeFragment 按需触发。
 */
public class VideoRepository {

    /** 加载阶段，便于 ViewModel/Fragment 渲染对应的占位 UI。 */
    public enum LoadPhase { IDLE, LOADING_CACHE, SCANNING, COMPLETED }

    public interface ScanCallback {
        /** 阶段变化（始终在主线程）。 */
        @MainThread void onPhaseChanged(LoadPhase phase);
        /**
         * 扫描中每来一批新视频（主线程）。
         * UI 应直接 append 到列表末尾，不重置分页。
         */
        @MainThread void onBatchLoaded(List<FileItem> videoBatch);
        /** 扫描完成（主线程）。allFiles = 全部文件，videoFiles = 全部视频（无排序）。 */
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

    // 当前正在执行的扫描是否应被取消（用于防止旧扫描结果污染新请求）
    private volatile long currentScanToken = 0;

    private VideoRepository(Context appCtx) {
        this.appCtx = appCtx;
        this.cacheManager = FileListCacheManager.getInstance(appCtx);
    }

    /**
     * 加载视频文件列表。
     *  - forceRefresh=false && 缓存有效 → 直接回调 onScanComplete（原始顺序，不排序）
     *  - 否则 → 扫盘，每扫到视频文件立即 onBatchLoaded，扫完 onScanComplete
     */
    public void loadVideos(FileItem.FileType filterType, boolean forceRefresh, ScanCallback cb) {
        final long token = ++currentScanToken; // 每次新请求生成新 token
        notifyPhase(cb, LoadPhase.LOADING_CACHE);
        io.execute(() -> {
            // 1) 缓存命中
            if (!forceRefresh) {
                List<FileItem> cached = cacheManager.loadFileList(true);
                if (cached != null && !cached.isEmpty()) {
                    List<FileItem> videos = filterVideos(cached); // 不排序
                    main.post(() -> {
                        if (token != currentScanToken) return; // 过期请求忽略
                        if (cb != null) {
                            cb.onScanComplete(cached, videos);
                            cb.onPhaseChanged(LoadPhase.COMPLETED);
                        }
                    });
                    return;
                }
            }
            // 2) 扫盘
            main.post(() -> { if (token == currentScanToken && cb != null) cb.onPhaseChanged(LoadPhase.SCANNING); });
            List<File> targets = collectAllStorageDirectories();
            final List<FileItem> allScanned = new ArrayList<>();
            final List<FileItem> allVideos = new ArrayList<>();

            FileUtils.scanAllFilesAsync(targets, filterType, new FileUtils.ScanCallback() {
                @Override public void onBatchLoaded(List<FileItem> batch) {
                    List<FileItem> newVideos = new ArrayList<>();
                    for (FileItem fi : batch) {
                        if (fi.getFileType() == FileItem.FileType.VIDEO) newVideos.add(fi);
                    }
                    main.post(() -> {
                        if (token != currentScanToken) return;
                        synchronized (allScanned) {
                            allScanned.addAll(batch);
                            allVideos.addAll(newVideos);
                        }
                        if (cb != null && !newVideos.isEmpty()) cb.onBatchLoaded(newVideos);
                    });
                }

                @Override public void onScanComplete(List<FileItem> finalList) {
                    main.post(() -> {
                        if (token != currentScanToken) return;
                        // 保存缓存（不排序，保持原始扫描顺序）
                        io.execute(() -> cacheManager.saveFileList(new ArrayList<>(allScanned), true));
                        if (cb != null) {
                            cb.onScanComplete(new ArrayList<>(allScanned), new ArrayList<>(allVideos));
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
    private List<FileItem> filterVideos(List<FileItem> all) {
        List<FileItem> videos = new ArrayList<>();
        for (FileItem item : all) {
            if (item.getFileType() == FileItem.FileType.VIDEO) videos.add(item);
        }
        return videos; // 不排序
    }

    private void notifyPhase(ScanCallback cb, LoadPhase phase) {
        if (cb == null) return;
        main.post(() -> cb.onPhaseChanged(phase));
    }
}
