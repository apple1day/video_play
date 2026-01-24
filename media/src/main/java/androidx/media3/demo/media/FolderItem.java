package androidx.media3.demo.media;

import java.io.File;

/**
 * 文件夹项数据类
 */
public class FolderItem {
    private File folder;
    private int videoCount;
    private long totalSize;
    
    public FolderItem(File folder, int videoCount, long totalSize) {
        this.folder = folder;
        this.videoCount = videoCount;
        this.totalSize = totalSize;
    }
    
    public File getFolder() {
        return folder;
    }
    
    public String getFolderName() {
        return folder.getName();
    }
    
    public String getFolderPath() {
        return folder.getAbsolutePath();
    }
    
    public int getVideoCount() {
        return videoCount;
    }
    
    public long getTotalSize() {
        return totalSize;
    }
    
    public String getFormattedSize() {
        return FileItem.formatSize(totalSize);
    }
}
