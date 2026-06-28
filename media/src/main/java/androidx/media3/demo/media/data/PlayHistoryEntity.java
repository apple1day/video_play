package androidx.media3.demo.media.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 播放历史 / 续播进度。
 * 每个文件一条记录；同时承担"上次观看时间"和"上次播放位置"两件事。
 */
@Entity(tableName = "play_history")
public class PlayHistoryEntity {

    @PrimaryKey
    @NonNull
    public String filePath = "";

    /** 上次播放位置（毫秒）。 */
    public long position;

    /** 视频时长（毫秒，0 表示未知）。 */
    public long duration;

    /** 最后一次保存进度的时间。 */
    public long updatedAt;

    public PlayHistoryEntity() {}

    @Ignore
    public PlayHistoryEntity(@NonNull String filePath, long position, long duration, long updatedAt) {
        this.filePath = filePath;
        this.position = position;
        this.duration = duration;
        this.updatedAt = updatedAt;
    }

    /** 进度百分比 0-100。 */
    public int progressPercent() {
        if (duration <= 0) return 0;
        return (int) ((position * 100L) / duration);
    }
}
