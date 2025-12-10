package com.anxiong.mv;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 视频项数据类
 */
public class VideoItem implements Parcelable {
    private final long id;
    private final String title;
    private final String path;
    private final String uri;
    private final long duration;
    private final long dateModified;

    public VideoItem(long id, String title, String path, String uri, long duration, long dateModified) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.uri = uri;
        this.duration = duration;
        this.dateModified = dateModified;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getPath() {
        return path;
    }

    public String getUri() {
        return uri;
    }

    public long getDuration() {
        return duration;
    }

    public long getDateModified() {
        return dateModified;
    }

    /**
     * Parcelable Creator
     */
    public static final Creator<VideoItem> CREATOR = new Creator<VideoItem>() {
        @Override
        public VideoItem createFromParcel(Parcel in) {
            return new VideoItem(
                    in.readLong(),
                    in.readString(),
                    in.readString(),
                    in.readString(),
                    in.readLong(),
                    in.readLong()
            );
        }

        @Override
        public VideoItem[] newArray(int size) {
            return new VideoItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(path);
        dest.writeString(uri);
        dest.writeLong(duration);
        dest.writeLong(dateModified);
    }
}
