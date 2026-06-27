package androidx.media3.demo.media.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PendingDeleteDao {

    @Query("SELECT COUNT(*) FROM pending_delete WHERE filePath = :path")
    int countByPath(String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PendingDeleteEntity entity);

    @Query("DELETE FROM pending_delete WHERE filePath = :path")
    void deleteByPath(String path);

    @Query("DELETE FROM pending_delete")
    void clear();

    /** 按打标时间升序——先标先删，行为与原 LinkedHashSet 顺序一致。 */
    @Query("SELECT filePath FROM pending_delete ORDER BY markedAt ASC")
    List<String> listAllPaths();

    @Query("SELECT COUNT(*) FROM pending_delete")
    int count();
}
