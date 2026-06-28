package androidx.media3.demo.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
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

    // ── L1 内存 LRU ──────────────────────────────────────────────
    private static final LruCache<String, Bitmap> memCache;
    static {
        // 用可用堆的 1/8 做缓存，但最多不超过 50MB
        final int maxMem = (int) (Runtime.getRuntime().maxMemory() / 1024); // KB
        final int cacheSize = Math.min(maxMem / 8, 50 * 1024);             // KB
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
                .override(200, 200);

        Glide.with(context)
                .asBitmap()
                .load(videoFile)
                .apply(options)
                .into(new com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@androidx.annotation.NonNull Bitmap resource,
                                                @androidx.annotation.Nullable com.bumptech.glide.request.transition.Transition<? super Bitmap> transition) {
                        memCache.put(key, resource);
                        imageView.setImageBitmap(resource);
                    }
                    @Override
                    public void onLoadCleared(@androidx.annotation.Nullable android.graphics.drawable.Drawable placeholder) {
                        // 资源被释放时不做处理（ImageView已持有Bitmap引用）
                    }
                });
    }

    /**
     * 清除 ImageView 的缩略图加载请求（在 ViewHolder 回收时调用）。
     */
    public static void clearThumbnail(Context context, ImageView imageView) {
        if (context != null && imageView != null) {
            Glide.with(context).clear(imageView);
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
            Bitmap bmp = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bmp == null) bmp = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bmp != null) memCache.put(key, bmp); // 写入 L1
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

