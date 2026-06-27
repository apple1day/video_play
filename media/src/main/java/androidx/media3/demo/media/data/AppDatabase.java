package androidx.media3.demo.media.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * 全局 Room 数据库。
 *
 * 设计：四张独立的小表（favorites / watched / pending_delete / play_history），
 * 每张表的主键都是 filePath。这样原来 4 个 SharedPreferences 的语义可以一一映射，
 * 同时还能跨表 JOIN —— 例如以后做"按播放最近时间排序但只看收藏"非常便宜。
 *
 * 注意：项目 build.gradle 中已通过 annotationProcessorOptions 配了 room.schemaLocation，
 *      运行 :media:assembleDebug 后会在 media/schemas/ 生成 1.json。
 */
@Database(
    entities = {
        FavoriteEntity.class,
        WatchedEntity.class,
        PendingDeleteEntity.class,
        PlayHistoryEntity.class
    },
    version = 1,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FavoriteDao favoriteDao();
    public abstract WatchedDao watchedDao();
    public abstract PendingDeleteDao pendingDeleteDao();
    public abstract PlayHistoryDao playHistoryDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "anxiong_media.db")
                        // 读多写少；允许主线程查询，避免上层"是否收藏"这类高频同步判断改全套异步。
                        // 写操作仍然走 IO 线程（通过 DataRepository）。
                        .allowMainThreadQueries()
                        // 升级策略：当前只有 v1，未来加表/字段时改成 addMigrations(...)。
                        .fallbackToDestructiveMigration()
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
