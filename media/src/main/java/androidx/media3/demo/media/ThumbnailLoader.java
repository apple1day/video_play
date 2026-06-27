package androidx.media3.demo.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;

/**
 * 视频缩略图加载工具类
 */
public class ThumbnailLoader {
    
    private static final String TAG = "ThumbnailLoader";
    
    /**
     * 加载视频缩略图到ImageView
     * 优化：使用文件路径作为签名，确保排序后缩略图正确更新
     * @param context 上下文
     * @param videoFile 视频文件
     * @param imageView 目标ImageView
     */
    public static void loadThumbnail(Context context, File videoFile, ImageView imageView) {
        if (context == null || videoFile == null || imageView == null) {
            return;
        }
        
        if (!videoFile.exists()) {
            // 只在文件不存在时清除并设置默认图标
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            return;
        }
        
        // 使用文件路径+修改时间作为缓存key，避免缓存混淆
        String cacheKey = videoFile.getAbsolutePath() + "_" + videoFile.lastModified();
        
        // 优化：降低缩略图分辨率至200x200，减少内存占用和加载时间
        RequestOptions options = new RequestOptions()
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存所有版本
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .signature(new ObjectKey(cacheKey)) // 使用唯一签名避免缓存混淆
                .override(200, 200); // 优化：降低分辨率（原300x300）
        
        // 优化：移除clear()调用，让Glide自己管理请求，避免快速滚动时的闪烁
        // 只在文件不存在时才清除，正常加载时让Glide复用请求
        
        // 加载新缩略图
        Glide.with(context)
                .asBitmap()
                .load(videoFile)
                .apply(options)
                .into(imageView);
    }
    
    /**
     * 清除ImageView的缩略图加载请求
     * 在ViewHolder回收或排序前调用
     */
    public static void clearThumbnail(Context context, ImageView imageView) {
        if (context != null && imageView != null) {
            Glide.with(context).clear(imageView);
        }
    }
    
    /**
     * 提取视频第一帧作为缩略图
     * @param videoPath 视频文件路径
     * @return 缩略图Bitmap，失败返回null
     */
    public static Bitmap extractThumbnail(String videoPath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoPath);
            // 优先取视频中点（视觉上更具代表性，避免黑屏开场）；失败回退到 1 秒
            long durationMs = 0L;
            try {
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationStr != null) durationMs = Long.parseLong(durationStr);
            } catch (Exception ignored) {}
            long targetUs = durationMs > 4000 ? (durationMs / 2) * 1000L : 1_000_000L;
            Bitmap bitmap = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bitmap == null) {
                bitmap = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract thumbnail: " + videoPath, e);
            return null;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to release retriever", e);
                }
            }
        }
    }
    
    /**
     * 获取视频时长
     * @param videoPath 视频文件路径
     * @return 时长（毫秒），失败返回0
     */
    public static long getVideoDuration(String videoPath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoPath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return durationStr != null ? Long.parseLong(durationStr) : 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get video duration: " + videoPath, e);
            return 0;
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to release retriever", e);
                }
            }
        }
    }
}
