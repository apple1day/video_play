package androidx.media3.demo.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;

/**
 * 视频缩略图加载工具类
 */
public class ThumbnailLoader {
    
    private static final String TAG = "ThumbnailLoader";
    
    /**
     * 加载视频缩略图到ImageView
     * @param context 上下文
     * @param videoFile 视频文件
     * @param imageView 目标ImageView
     */
    public static void loadThumbnail(Context context, File videoFile, ImageView imageView) {
        if (context == null || videoFile == null || imageView == null) {
            return;
        }
        
        if (!videoFile.exists()) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
            return;
        }
        
        RequestOptions options = new RequestOptions()
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery);
        
        Glide.with(context)
                .asBitmap()
                .load(videoFile)
                .apply(options)
                .into(imageView);
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
            // 提取1秒处的帧
            Bitmap bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
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
