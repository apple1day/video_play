package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.media3.demo.media.data.AppDatabase;
import androidx.media3.demo.media.data.FavoriteDao;
import androidx.media3.demo.media.data.FavoriteEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 收藏管理器。
 *
 * 数据存储已从 SharedPreferences 迁移到 Room（表：favorites）。
 * 对外 API 与旧版本完全兼容，所以调用方无需修改。
 *
 * 首次启动会把旧的 SP 数据自动迁移到 Room（只迁一次，迁完留下标记位）。
 */
public class FavoriteManager {

    private static final String LEGACY_PREF = "favorites";
    private static final String LEGACY_KEY = "favorite_files";
    private static final String MIGRATE_PREF = "anxiong_migrate";
    private static final String MIGRATE_FLAG = "favorites_migrated_v1";

    private static FavoriteManager instance;

    private final FavoriteDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private FavoriteManager(Context ctx) {
        this.dao = AppDatabase.get(ctx).favoriteDao();
        migrateFromSharedPrefsIfNeeded(ctx);
    }

    public static synchronized FavoriteManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoriteManager(context.getApplicationContext());
        }
        return instance;
    }

    public void addFavorite(String filePath) {
        if (filePath == null) return;
        dao.upsert(new FavoriteEntity(filePath, System.currentTimeMillis()));
    }

    public void removeFavorite(String filePath) {
        if (filePath == null) return;
        dao.deleteByPath(filePath);
    }

    public boolean toggleFavorite(String filePath) {
        if (isFavorite(filePath)) {
            removeFavorite(filePath);
            return false;
        } else {
            addFavorite(filePath);
            return true;
        }
    }

    public boolean isFavorite(String filePath) {
        if (filePath == null) return false;
        return dao.countByPath(filePath) > 0;
    }

    public Set<String> getFavorites() {
        return new HashSet<>(dao.listAllPaths());
    }

    public void clearAllFavorites() {
        io.execute(dao::clear);
    }

    /** 一次性把旧的 SP 数据迁到 Room，迁完写入标记位。 */
    private void migrateFromSharedPrefsIfNeeded(Context ctx) {
        SharedPreferences flag = ctx.getSharedPreferences(MIGRATE_PREF, Context.MODE_PRIVATE);
        if (flag.getBoolean(MIGRATE_FLAG, false)) return;
        SharedPreferences legacy = ctx.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE);
        Set<String> old = legacy.getStringSet(LEGACY_KEY, null);
        if (old != null && !old.isEmpty()) {
            long now = System.currentTimeMillis();
            for (String path : old) {
                if (path != null) dao.upsert(new FavoriteEntity(path, now));
            }
        }
        flag.edit().putBoolean(MIGRATE_FLAG, true).apply();
    }
}
