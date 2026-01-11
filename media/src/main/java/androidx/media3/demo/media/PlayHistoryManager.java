package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放历史管理器
 * 记录每个视频的播放位置和最后播放时间
 */
public class PlayHistoryManager {
    
    private static final String TAG = "PlayHistoryManager";
    private static final String PREF_NAME = "play_history";
    private static final String KEY_HISTORY = "history";
    private static final int MAX_HISTORY_SIZE = 100; // 最多保存100条历史记录
    
    private static PlayHistoryManager instance;
    private SharedPreferences preferences;
    
    private PlayHistoryManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized PlayHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new PlayHistoryManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 保存播放位置
     * @param filePath 视频文件路径
     * @param position 播放位置（毫秒）
     * @param duration 视频总时长（毫秒）
     */
    public void savePlayPosition(String filePath, long position, long duration) {
        try {
            JSONArray historyArray = getHistoryArray();
            
            // 查找是否已存在该视频的记录
            int existingIndex = -1;
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject item = historyArray.getJSONObject(i);
                if (item.getString("path").equals(filePath)) {
                    existingIndex = i;
                    break;
                }
            }
            
            // 创建新的记录
            JSONObject historyItem = new JSONObject();
            historyItem.put("path", filePath);
            historyItem.put("position", position);
            historyItem.put("duration", duration);
            historyItem.put("timestamp", System.currentTimeMillis());
            
            // 如果已存在，更新记录；否则添加新记录
            if (existingIndex >= 0) {
                historyArray.put(existingIndex, historyItem);
            } else {
                historyArray.put(historyItem);
            }
            
            // 限制历史记录数量（保留最新的）
            if (historyArray.length() > MAX_HISTORY_SIZE) {
                JSONArray newArray = new JSONArray();
                // 按时间戳排序，保留最新的
                List<JSONObject> items = new ArrayList<>();
                for (int i = 0; i < historyArray.length(); i++) {
                    items.add(historyArray.getJSONObject(i));
                }
                items.sort((a, b) -> {
                    try {
                        return Long.compare(b.getLong("timestamp"), a.getLong("timestamp"));
                    } catch (JSONException e) {
                        return 0;
                    }
                });
                for (int i = 0; i < MAX_HISTORY_SIZE; i++) {
                    newArray.put(items.get(i));
                }
                historyArray = newArray;
            }
            
            // 保存到SharedPreferences
            preferences.edit().putString(KEY_HISTORY, historyArray.toString()).apply();
            
            Log.d(TAG, "Saved play position: " + filePath + " -> " + position + "ms");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save play position", e);
        }
    }
    
    /**
     * 获取播放位置
     * @param filePath 视频文件路径
     * @return 播放位置（毫秒），如果没有记录返回0
     */
    public long getPlayPosition(String filePath) {
        try {
            JSONArray historyArray = getHistoryArray();
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject item = historyArray.getJSONObject(i);
                if (item.getString("path").equals(filePath)) {
                    return item.getLong("position");
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to get play position", e);
        }
        return 0;
    }
    
    /**
     * 获取播放历史列表（按时间倒序）
     * @return 历史记录列表
     */
    public List<PlayHistoryItem> getPlayHistory() {
        List<PlayHistoryItem> historyList = new ArrayList<>();
        try {
            JSONArray historyArray = getHistoryArray();
            
            // 转换为列表并按时间戳排序
            List<JSONObject> items = new ArrayList<>();
            for (int i = 0; i < historyArray.length(); i++) {
                items.add(historyArray.getJSONObject(i));
            }
            items.sort((a, b) -> {
                try {
                    return Long.compare(b.getLong("timestamp"), a.getLong("timestamp"));
                } catch (JSONException e) {
                    return 0;
                }
            });
            
            // 转换为PlayHistoryItem对象
            for (JSONObject item : items) {
                PlayHistoryItem historyItem = new PlayHistoryItem();
                historyItem.filePath = item.getString("path");
                historyItem.position = item.getLong("position");
                historyItem.duration = item.optLong("duration", 0);
                historyItem.timestamp = item.getLong("timestamp");
                historyList.add(historyItem);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to get play history", e);
        }
        return historyList;
    }
    
    /**
     * 清除指定视频的播放历史
     * @param filePath 视频文件路径
     */
    public void clearHistory(String filePath) {
        try {
            JSONArray historyArray = getHistoryArray();
            JSONArray newArray = new JSONArray();
            
            for (int i = 0; i < historyArray.length(); i++) {
                JSONObject item = historyArray.getJSONObject(i);
                if (!item.getString("path").equals(filePath)) {
                    newArray.put(item);
                }
            }
            
            preferences.edit().putString(KEY_HISTORY, newArray.toString()).apply();
            Log.d(TAG, "Cleared history for: " + filePath);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to clear history", e);
        }
    }
    
    /**
     * 清除所有播放历史
     */
    public void clearAllHistory() {
        preferences.edit().remove(KEY_HISTORY).apply();
        Log.d(TAG, "Cleared all play history");
    }
    
    /**
     * 获取历史记录数组
     */
    private JSONArray getHistoryArray() {
        String historyString = preferences.getString(KEY_HISTORY, "[]");
        try {
            return new JSONArray(historyString);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse history", e);
            return new JSONArray();
        }
    }
    
    /**
     * 播放历史项
     */
    public static class PlayHistoryItem {
        public String filePath;
        public long position;
        public long duration;
        public long timestamp;
        
        /**
         * 获取播放进度百分比
         */
        public int getProgress() {
            if (duration <= 0) {
                return 0;
            }
            return (int) ((position * 100) / duration);
        }
    }
}
