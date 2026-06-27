package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.media3.demo.media.data.AppDatabase;
import androidx.media3.demo.media.data.PlayHistoryDao;
import androidx.media3.demo.media.data.PlayHistoryEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放历史 / 续播进度管理器。
 *
 * 存储迁移到 Room（表：play_history）。
 * 公共 API 完全兼容老版本，且 PlayHistoryItem 仍然以 public 字段方式暴露给现有调用方。
 */
public class PlayHistoryManager {

    private static final String TAG = "PlayHistoryManager";
    private static final int MAX_HISTORY_SIZE = 100;

    private static final String LEGACY_PREF = "play_history";
    private static final String LEGACY_KEY = "history";
    private static final String MIGRATE_PREF = "anxiong_migrate";
    private static final String MIGRATE_FLAG = "play_history_migrated_v1";

    private static PlayHistoryManager instance;

    private final PlayHistoryDao dao;

    private PlayHistoryManager(Context context) {
        this.dao = AppDatabase.get(context).playHistoryDao();
        migrateFromSharedPrefsIfNeeded(context);
    }

    public static synchronized PlayHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new PlayHistoryManager(context.getApplicationContext());
        }
        return instance;
    }

    public void savePlayPosition(String filePath, long position, long duration) {
        if (filePath == null) return;
        dao.upsert(new PlayHistoryEntity(filePath, position, duration, System.currentTimeMillis()));
        // 顺手修剪历史，保持上限
        dao.trim(MAX_HISTORY_SIZE);
        Log.d(TAG, "Saved play position: " + filePath + " -> " + position + "ms");
    }

    public long getPlayPosition(String filePath) {
        if (filePath == null) return 0L;
        Long pos = dao.getPositionByPath(filePath);
        return pos == null ? 0L : pos;
    }

    public List<PlayHistoryItem> getPlayHistory() {
        List<PlayHistoryItem> out = new ArrayList<>();
        for (PlayHistoryEntity e : dao.listRecent(MAX_HISTORY_SIZE)) {
            PlayHistoryItem item = new PlayHistoryItem();
            item.filePath = e.filePath;
            item.position = e.position;
            item.duration = e.duration;
            item.timestamp = e.updatedAt;
            out.add(item);
        }
        return out;
    }

    public void clearHistory(String filePath) {
        if (filePath == null) return;
        dao.deleteByPath(filePath);
        Log.d(TAG, "Cleared history for: " + filePath);
    }

    public void clearAllHistory() {
        dao.clear();
        Log.d(TAG, "Cleared all play history");
    }

    private void migrateFromSharedPrefsIfNeeded(Context ctx) {
        SharedPreferences flag = ctx.getSharedPreferences(MIGRATE_PREF, Context.MODE_PRIVATE);
        if (flag.getBoolean(MIGRATE_FLAG, false)) return;
        SharedPreferences legacy = ctx.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE);
        String raw = legacy.getString(LEGACY_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String path = o.optString("path", null);
                if (path == null) continue;
                long pos = o.optLong("position", 0L);
                long dur = o.optLong("duration", 0L);
                long ts = o.optLong("timestamp", System.currentTimeMillis());
                dao.upsert(new PlayHistoryEntity(path, pos, dur, ts));
            }
            Log.d(TAG, "Migrated " + arr.length() + " play history entries from SP to Room");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to migrate play history", e);
        }
        flag.edit().putBoolean(MIGRATE_FLAG, true).apply();
    }

    /**
     * 播放历史项 (保持旧 API 形态，调用方无需修改)。
     */
    public static class PlayHistoryItem {
        public String filePath;
        public long position;
        public long duration;
        public long timestamp;

        public int getProgress() {
            if (duration <= 0) return 0;
            return (int) ((position * 100) / duration);
        }
    }
}
