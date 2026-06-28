package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.media3.demo.media.data.AppDatabase;
import androidx.media3.demo.media.data.WatchedDao;
import androidx.media3.demo.media.data.WatchedEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 已观看视频管理器。
 *
 * 存储已迁移到 Room（表：watched）。原内存缓存（HashSet）保留，
 * 同步写入数据库 + 内存集，读时优先用内存集——保证 UI 上"是否已观看"判断仍然零延迟。
 *
 * 首次启动迁移：旧 SP 是 JSONArray，逐条转成 WatchedEntity 写入。
 */
public class WatchedManager {

    private static final String TAG = "WatchedManager";
    private static final String LEGACY_PREF = "watched_videos";
    private static final String LEGACY_KEY = "watched";
    private static final String MIGRATE_PREF = "anxiong_migrate";
    private static final String MIGRATE_FLAG = "watched_migrated_v1";

    private static final float WATCH_THRESHOLD = 0.8f;

    private static WatchedManager instance;

    private final WatchedDao dao;
    // 内存缓存：仅用于快速读取，写操作同时更新内存 + 异步写 DB
    private final Set<String> watchedVideos = java.util.Collections.synchronizedSet(new HashSet<>());
    // 异步写 DB 的线程池（单线程，按顺序执行，避免并发冲突）
    private static final java.util.concurrent.ExecutorService dbWriter =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private WatchedManager(Context context) {
        this.dao = AppDatabase.get(context).watchedDao();
        migrateFromSharedPrefsIfNeeded(context);
        watchedVideos.addAll(dao.listAllPaths());
        Log.d(TAG, "Loaded " + watchedVideos.size() + " watched videos");
    }

    public static synchronized WatchedManager getInstance(Context context) {
        if (instance == null) {
            instance = new WatchedManager(context.getApplicationContext());
        }
        return instance;
    }

    public boolean isWatched(String filePath) {
        return filePath != null && watchedVideos.contains(filePath);
    }

    public void markAsWatched(String filePath) {
        if (filePath == null) return;
        if (watchedVideos.add(filePath)) {
            // 异步写 DB，不阻塞调用方（通常在主线程）
            dbWriter.execute(() ->
                dao.upsert(new WatchedEntity(filePath, System.currentTimeMillis())));
            Log.d(TAG, "Marked as watched: " + filePath);
        }
    }

    public void unmarkAsWatched(String filePath) {
        if (filePath == null) return;
        if (watchedVideos.remove(filePath)) {
            dbWriter.execute(() -> dao.deleteByPath(filePath));
            Log.d(TAG, "Unmarked as watched: " + filePath);
        }
    }

    public boolean toggleWatched(String filePath) {
        if (isWatched(filePath)) {
            unmarkAsWatched(filePath);
            return false;
        } else {
            markAsWatched(filePath);
            return true;
        }
    }

    public boolean checkAndMarkWatched(String filePath, long position, long duration) {
        if (filePath == null || duration <= 0 || position <= 0) return false;
        if (isWatched(filePath)) return false;
        float progress = (float) position / duration;
        if (progress >= WATCH_THRESHOLD) {
            markAsWatched(filePath);
            Log.d(TAG, "Auto-marked as watched (progress: " + (int) (progress * 100) + "%): " + filePath);
            return true;
        }
        return false;
    }

    public List<String> getAllWatchedVideos() {
        return new ArrayList<>(watchedVideos);
    }

    public int getWatchedCount() {
        return watchedVideos.size();
    }

    public void markMultipleAsWatched(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<WatchedEntity> batch = new ArrayList<>();
        for (String p : filePaths) {
            if (p == null) continue;
            if (watchedVideos.add(p)) batch.add(new WatchedEntity(p, now));
        }
        if (!batch.isEmpty()) {
            final List<WatchedEntity> toInsert = batch;
            dbWriter.execute(() -> {
                dao.insertAll(toInsert);
                Log.d(TAG, "Marked " + toInsert.size() + " videos as watched");
            });
        }
    }

    public void unmarkMultipleAsWatched(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) return;
        List<String> removed = new ArrayList<>();
        for (String p : filePaths) {
            if (p != null && watchedVideos.remove(p)) removed.add(p);
        }
        if (!removed.isEmpty()) {
            final List<String> toDelete = removed;
            dbWriter.execute(() -> {
                dao.deleteByPaths(toDelete);
                Log.d(TAG, "Unmarked " + toDelete.size() + " videos as watched");
            });
        }
    }

    public void clearAllWatched() {
        watchedVideos.clear();
        dbWriter.execute(() -> {
            dao.clear();
            Log.d(TAG, "Cleared all watched videos");
        });
    }

    public void cleanupNonExistentVideos(Set<String> existingPaths) {
        if (existingPaths == null) return;
        Set<String> toRemove = new HashSet<>();
        for (String p : watchedVideos) {
            if (!existingPaths.contains(p)) toRemove.add(p);
        }
        if (!toRemove.isEmpty()) {
            watchedVideos.removeAll(toRemove);
            dao.deleteByPaths(new ArrayList<>(toRemove));
            Log.d(TAG, "Cleaned up " + toRemove.size() + " non-existent videos");
        }
    }

    private void migrateFromSharedPrefsIfNeeded(Context ctx) {
        SharedPreferences flag = ctx.getSharedPreferences(MIGRATE_PREF, Context.MODE_PRIVATE);
        if (flag.getBoolean(MIGRATE_FLAG, false)) return;
        SharedPreferences legacy = ctx.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE);
        String raw = legacy.getString(LEGACY_KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            List<WatchedEntity> batch = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String path = o.optString("path", null);
                if (path == null) continue;
                long ts = o.optLong("timestamp", System.currentTimeMillis());
                batch.add(new WatchedEntity(path, ts));
            }
            if (!batch.isEmpty()) {
                dao.insertAll(batch);
                Log.d(TAG, "Migrated " + batch.size() + " watched entries from SP to Room");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to migrate watched videos", e);
        }
        flag.edit().putBoolean(MIGRATE_FLAG, true).apply();
    }
}
