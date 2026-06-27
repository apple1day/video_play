package androidx.media3.demo.media.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 收藏的视频。filePath = 主键（与之前 SharedPreferences 的 key 语义一致）。
 */
@Entity(tableName = "favorites")
public class FavoriteEntity {

    @PrimaryKey
    @NonNull
    public String filePath = "";

    /** 收藏的时间戳，方便将来按时间倒序展示。*/
    public long createdAt;

    public FavoriteEntity() {}

    public FavoriteEntity(@NonNull String filePath, long createdAt) {
        this.filePath = filePath;
        this.createdAt = createdAt;
    }
}
