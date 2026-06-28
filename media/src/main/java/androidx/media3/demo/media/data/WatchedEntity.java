package androidx.media3.demo.media.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** 已观看（播放过 ≥80%）的视频。 */
@Entity(tableName = "watched")
public class WatchedEntity {

    @PrimaryKey
    @NonNull
    public String filePath = "";

    public long watchedAt;

    public WatchedEntity() {}

    @Ignore
    public WatchedEntity(@NonNull String filePath, long watchedAt) {
        this.filePath = filePath;
        this.watchedAt = watchedAt;
    }
}
