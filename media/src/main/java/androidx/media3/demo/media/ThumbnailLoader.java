package androidx.media3.demo.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;

/**
 * 视频缩略图加载工具类。
 *
 * 两级缓存策略：
 *  - L1 内存 LRU（{@link #memCache}）：容量 = 可用堆的 1/8，最多 100 张；命中时直接返回，无 IO。
 *  - L2 Glide 磁盘缓存：用于 ImageView 加载，Glide 本身已做二级缓存。
 *
 * extractThumbnail（供 PlaybackService 通知封面使用）也走 L1，避免重复解码同一视频。
 */
public class ThumbnailLoader {

    private static final String TAG = "ThumbnailLoader";
    private static final int THUMBNAIL_SIZE = 200;
    private static final int NOTIFICATION_COVER_MAX_SIZE = 320;

    // ── L1 内存 LRU ──────────────────────────────────────────────
    private static final LruCache<String, Bitmap> memCache;
    static {
        // Glide 已有内存/磁盘缓存，这里只保留小型热缓存，避免列表和通知封面占用过多 Bitmap native 内存。
        final int maxMem = (int) (Runtime.getRuntime().maxMemory() / 1024); // KB
        final int cacheSize = Math.min(Math.max(maxMem / 16, 4 * 1024), 12 * 1024); // KB
        memCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024; // KB
            }
        };
    }

    private static String cacheKey(String path) {
        File f = new File(path);
        return path + "_" + f.lastModified();
    }

    /**
     * 加载视频缩略图到 ImageView（Glide 二级缓存 + L1 内存 LRU）。
     */
    public static void loadThumbnail(Context context, File videoFile, ImageView imageView) {
        if (context == null || videoFile == null || imageView == null) return;

        if (!videoFile.exists()) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            return;
        }

        String key = cacheKey(videoFile.getAbsolutePath());

        // 先查 L1
        Bitmap cached = memCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            imageView.setImageBitmap(cached);
            return;
        }

        RequestOptions options = new RequestOptions()
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .signature(new ObjectKey(key))
                .override(THUMBNAIL_SIZE, THUMBNAIL_SIZE);

        Glide.with(context)
                .asBitmap()
                .load(videoFile)
                .apply(options)
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@androidx.annotation.Nullable GlideException e,
                                                Object model,
                                                Target<Bitmap> target,
                                                boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource,
                                                   Object model,
                                                   Target<Bitmap> target,
                                                   DataSource dataSource,
                                                   boolean isFirstResource) {
                        memCache.put(key, resource);
                        return false;
                    }
                })
                .into(imageView);
    }

    /**
     * 清除 ImageView 的缩略图加载请求（在 ViewHolder 回收时调用）。
     */
    public static void clearThumbnail(Context context, ImageView imageView) {
        if (context != null && imageView != null) {
            try {
                Glide.with(context).clear(imageView);
            } catch (IllegalArgumentException ignored) {
                // Activity 已销毁时 Glide.with(activity) 可能拒绝清理；直接清空 ImageView 引用兜底。
                imageView.setImageDrawable(null);
            }
        }
    }

    /**
     * 提取视频封面帧（同步，应在 IO 线程调用）。
     * 优先从 L1 内存缓存返回，命中时零 IO。
     */
    public static Bitmap extractThumbnail(String videoPath) {
        String key = cacheKey(videoPath);
        Bitmap cached = memCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;

        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoPath);
            long durationMs = 0L;
            try {
                String ds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (ds != null) durationMs = Long.parseLong(ds);
            } catch (Exception ignored) {}
            long targetUs = durationMs > 4000 ? (durationMs / 2) * 1000L : 1_000_000L;
            Bitmap bmp = getScaledVideoFrame(retriever, targetUs, NOTIFICATION_COVER_MAX_SIZE);
            if (bmp == null) bmp = getScaledVideoFrame(retriever, 1_000_000L, NOTIFICATION_COVER_MAX_SIZE);
            if (bmp != null) memCache.put(key, bmp); // 写入 L1，只缓存小尺寸封面
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract thumbnail: " + videoPath, e);
            return null;
        } finally {
            if (retriever != null) {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static Bitmap getScaledVideoFrame(MediaMetadataRetriever retriever, long timeUs, int maxSize) {
        try {
            int[] size = readScaledVideoSize(retriever, maxSize);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && size[0] > 0 && size[1] > 0) {
                return retriever.getScaledFrameAtTime(timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size[0], size[1]);
            }
        } catch (Throwable ignored) {}

        Bitmap original = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        return scaleDown(original, maxSize);
    }

    private static int[] readScaledVideoSize(MediaMetadataRetriever retriever, int maxSize) {
        int width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        if (width <= 0 || height <= 0) return new int[] { maxSize, maxSize };
        float scale = Math.min(1f, maxSize / (float) Math.max(width, height));
        return new int[] { Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)) };
    }

    private static int parseInt(String value) {
        try { return value != null ? Integer.parseInt(value) : 0; } catch (Exception ignored) { return 0; }
    }

    private static Bitmap scaleDown(Bitmap original, int maxSize) {
        if (original == null) return null;
        int width = original.getWidth();
        int height = original.getHeight();
        int maxEdge = Math.max(width, height);
        if (maxEdge <= maxSize) return original;
        float scale = maxSize / (float) maxEdge;
        Bitmap scaled = Bitmap.createScaledBitmap(original,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
        if (scaled != original) original.recycle();
        return scaled;
    }

    /**
     * 获取视频时长（毫秒），失败返回 0。
     */
    public static long getVideoDuration(String videoPath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoPath);
            String ds = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return ds != null ? Long.parseLong(ds) : 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get video duration: " + videoPath, e);
            return 0;
        } finally {
            if (retriever != null) {
                try { retriever.release(); } catch (Exception ignored) {}
            }
        }
    }
}

