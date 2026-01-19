package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 已观看视频管理器
 * 管理已观看过的视频列表，方便用户筛选和管理
 */
public class WatchedManager {
    
    private static final String TAG = "WatchedManager";
    private static final String PREF_NAME = "watched_videos";
    private static final String KEY_WATCHED = "watched";
    private static final float WATCH_THRESHOLD = 0.8f; // 播放80%视为已观看
    
    private static WatchedManager instance;
    private SharedPreferences preferences;
    private Set<String> watchedVideos;
    
    private WatchedManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadWatchedVideos();
    }
    
    public static synchronized WatchedManager getInstance(Context context) {
        if (instance == null) {
            instance = new WatchedManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 从SharedPreferences加载已观看列表
     */
    private void loadWatchedVideos() {
        watchedVideos = new HashSet<>();
        String watchedString = preferences.getString(KEY_WATCHED, "[]");
        try {
            JSONArray jsonArray = new JSONArray(watchedString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                watchedVideos.add(item.getString("path"));
            }
            Log.d(TAG, "Loaded " + watchedVideos.size() + " watched videos");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load watched videos", e);
        }
    }
    
    /**
     * 保存已观看列表到SharedPreferences
     */
    private void saveWatchedVideos() {
        try {
            JSONArray jsonArray = new JSONArray();
            long timestamp = System.currentTimeMillis();
            for (String path : watchedVideos) {
                JSONObject item = new JSONObject();
                item.put("path", path);
                item.put("timestamp", timestamp);
                jsonArray.put(item);
            }
            preferences.edit().putString(KEY_WATCHED, jsonArray.toString()).apply();
            Log.d(TAG, "Saved " + watchedVideos.size() + " watched videos");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save watched videos", e);
        }
    }
    
    /**
     * 检查视频是否已观看
     * @param filePath 视频文件路径
     * @return true表示已观看
     */
    public boolean isWatched(String filePath) {
        return watchedVideos.contains(filePath);
    }
    
    /**
     * 标记视频为已观看
     * @param filePath 视频文件路径
     */
    public void markAsWatched(String filePath) {
        if (!watchedVideos.contains(filePath)) {
            watchedVideos.add(filePath);
            saveWatchedVideos();
            Log.d(TAG, "Marked as watched: " + filePath);
        }
    }
    
    /**
     * 取消标记已观看
     * @param filePath 视频文件路径
     */
    public void unmarkAsWatched(String filePath) {
        if (watchedVideos.remove(filePath)) {
            saveWatchedVideos();
            Log.d(TAG, "Unmarked as watched: " + filePath);
        }
    }
    
    /**
     * 切换已观看状态
     * @param filePath 视频文件路径
     * @return 切换后的状态（true表示已观看）
     */
    public boolean toggleWatched(String filePath) {
        if (isWatched(filePath)) {
            unmarkAsWatched(filePath);
            return false;
        } else {
            markAsWatched(filePath);
            return true;
        }
    }
    
    /**
     * 检查播放进度并自动标记为已观看
     * @param filePath 视频文件路径
     * @param position 当前播放位置（毫秒）
     * @param duration 视频总时长（毫秒）
     * @return 如果刚刚被标记为已观看返回true
     */
    public boolean checkAndMarkWatched(String filePath, long position, long duration) {
        if (duration <= 0 || position <= 0) {
            return false;
        }
        
        // 如果已经标记过，不再重复处理
        if (isWatched(filePath)) {
            return false;
        }
        
        // 计算播放进度
        float progress = (float) position / duration;
        
        // 如果播放超过80%，自动标记为已观看
        if (progress >= WATCH_THRESHOLD) {
            markAsWatched(filePath);
            Log.d(TAG, "Auto-marked as watched (progress: " + (int)(progress * 100) + "%): " + filePath);
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取所有已观看视频的路径列表
     * @return 已观看视频路径列表
     */
    public List<String> getAllWatchedVideos() {
        return new ArrayList<>(watchedVideos);
    }
    
    /**
     * 获取已观看视频数量
     * @return 已观看视频数量
     */
    public int getWatchedCount() {
        return watchedVideos.size();
    }
    
    /**
     * 批量标记为已观看
     * @param filePaths 视频文件路径列表
     */
    public void markMultipleAsWatched(List<String> filePaths) {
        boolean changed = false;
        for (String filePath : filePaths) {
            if (!watchedVideos.contains(filePath)) {
                watchedVideos.add(filePath);
                changed = true;
            }
        }
        if (changed) {
            saveWatchedVideos();
            Log.d(TAG, "Marked " + filePaths.size() + " videos as watched");
        }
    }
    
    /**
     * 批量取消已观看标记
     * @param filePaths 视频文件路径列表
     */
    public void unmarkMultipleAsWatched(List<String> filePaths) {
        boolean changed = false;
        for (String filePath : filePaths) {
            if (watchedVideos.remove(filePath)) {
                changed = true;
            }
        }
        if (changed) {
            saveWatchedVideos();
            Log.d(TAG, "Unmarked " + filePaths.size() + " videos as watched");
        }
    }
    
    /**
     * 清除所有已观看标记
     */
    public void clearAllWatched() {
        watchedVideos.clear();
        saveWatchedVideos();
        Log.d(TAG, "Cleared all watched videos");
    }
    
    /**
     * 移除不存在的视频（清理缓存）
     * @param existingPaths 当前存在的视频路径集合
     */
    public void cleanupNonExistentVideos(Set<String> existingPaths) {
        boolean changed = false;
        Set<String> toRemove = new HashSet<>();
        
        for (String path : watchedVideos) {
            if (!existingPaths.contains(path)) {
                toRemove.add(path);
                changed = true;
            }
        }
        
        if (changed) {
            watchedVideos.removeAll(toRemove);
            saveWatchedVideos();
            Log.d(TAG, "Cleaned up " + toRemove.size() + " non-existent videos");
        }
    }
}
