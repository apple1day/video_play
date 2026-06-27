package androidx.media3.demo.media.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface WatchedDao {

    @Query("SELECT COUNT(*) FROM watched WHERE filePath = :path")
    int countByPath(String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(WatchedEntity entity);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<WatchedEntity> entities);

    @Query("DELETE FROM watched WHERE filePath = :path")
    void deleteByPath(String path);

    @Query("DELETE FROM watched WHERE filePath IN (:paths)")
    void deleteByPaths(List<String> paths);

    @Query("DELETE FROM watched")
    void clear();

    @Query("SELECT filePath FROM watched")
    List<String> listAllPaths();

    @Query("DELETE FROM watched WHERE filePath NOT IN (:keep)")
    void retainOnly(List<String> keep);
}
