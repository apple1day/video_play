package androidx.media3.demo.media.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PlayHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PlayHistoryEntity entity);

    @Query("SELECT * FROM play_history WHERE filePath = :path LIMIT 1")
    PlayHistoryEntity findByPath(String path);

    @Query("SELECT position FROM play_history WHERE filePath = :path LIMIT 1")
    Long getPositionByPath(String path);

    @Query("DELETE FROM play_history WHERE filePath = :path")
    void deleteByPath(String path);

    @Query("DELETE FROM play_history")
    void clear();

    /** 最近播放优先；最多取 maxSize 条。 */
    @Query("SELECT * FROM play_history ORDER BY updatedAt DESC LIMIT :maxSize")
    List<PlayHistoryEntity> listRecent(int maxSize);

    /** 修剪历史：保留前 maxSize 条最新的，旧的全部删除。 */
    @Query("DELETE FROM play_history WHERE filePath NOT IN ("
            + "SELECT filePath FROM play_history ORDER BY updatedAt DESC LIMIT :maxSize)")
    void trim(int maxSize);
}
