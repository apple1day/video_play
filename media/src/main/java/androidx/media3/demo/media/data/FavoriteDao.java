package androidx.media3.demo.media.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoriteDao {

    @Query("SELECT COUNT(*) FROM favorites WHERE filePath = :path")
    int countByPath(String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(FavoriteEntity entity);

    @Query("DELETE FROM favorites WHERE filePath = :path")
    void deleteByPath(String path);

    @Query("DELETE FROM favorites")
    void clear();

    @Query("SELECT filePath FROM favorites")
    List<String> listAllPaths();
}
