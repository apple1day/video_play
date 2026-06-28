package androidx.media3.demo.media.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** 待删除（标记后稍后批量清理）。 */
@Entity(tableName = "pending_delete")
public class PendingDeleteEntity {

    @PrimaryKey
    @NonNull
    public String filePath = "";

    public long markedAt;

    public PendingDeleteEntity() {}

    @Ignore
    public PendingDeleteEntity(@NonNull String filePath, long markedAt) {
        this.filePath = filePath;
        this.markedAt = markedAt;
    }
}
