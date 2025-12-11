package androidx.media3.demo.filebrowser;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    
    public static List<File> getStorageDirectories(Context context) {
        List<File> directories = new ArrayList<>();
        
        // 内部存储
        File internalStorage = Environment.getDataDirectory();
        if (internalStorage != null && internalStorage.exists()) {
            directories.add(internalStorage);
        }
        
        // 外部存储
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directories.addAll(getStorageDirectoriesApi30(context));
        } else {
            File externalStorage = Environment.getExternalStorageDirectory();
            if (externalStorage != null && externalStorage.exists()) {
                directories.add(externalStorage);
            }
        }
        
        return directories;
    }
    
    @RequiresApi(api = Build.VERSION_CODES.R)
    private static List<File> getStorageDirectoriesApi30(Context context) {
        List<File> directories = new ArrayList<>();
        StorageManager storageManager = context.getSystemService(StorageManager.class);
        if (storageManager != null) {
            List<StorageVolume> volumes = storageManager.getStorageVolumes();
            for (StorageVolume volume : volumes) {
                if (volume.getState().equals(Environment.MEDIA_MOUNTED)) {
                    File directory = volume.getDirectory();
                    if (directory != null && directory.exists()) {
                        directories.add(directory);
                    }
                }
            }
        }
        return directories;
    }
    
    public static List<FileItem> scanFiles(File directory, FileItem.FileType filterType) {
        List<FileItem> files = new ArrayList<>();
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return files;
        }
        
        File[] fileList = directory.listFiles();
        if (fileList == null) {
            return files;
        }
        
        for (File file : fileList) {
            if (file.isHidden()) {
                continue;
            }
            
            if (file.isDirectory()) {
                // 递归扫描子目录
                files.addAll(scanFiles(file, filterType));
            } else {
                FileItem item = new FileItem(file);
                if (filterType == null || filterType == FileItem.FileType.OTHER || 
                    item.getFileType() == filterType) {
                    files.add(item);
                }
            }
        }
        
        return files;
    }
    
    public static List<FileItem> scanAllFiles(List<File> storageDirectories, FileItem.FileType filterType) {
        List<FileItem> allFiles = new ArrayList<>();
        for (File directory : storageDirectories) {
            allFiles.addAll(scanFiles(directory, filterType));
        }
        return allFiles;
    }
    
    public static StorageInfo getStorageInfo(File directory) {
        if (directory == null || !directory.exists()) {
            return new StorageInfo(0, 0);
        }
        
        try {
            StatFs statFs = new StatFs(directory.getAbsolutePath());
            long totalBytes;
            long availableBytes;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                totalBytes = statFs.getTotalBytes();
                availableBytes = statFs.getAvailableBytes();
            } else {
                long blockSize = statFs.getBlockSize();
                long totalBlocks = statFs.getBlockCount();
                long availableBlocks = statFs.getAvailableBlocks();
                totalBytes = totalBlocks * blockSize;
                availableBytes = availableBlocks * blockSize;
            }
            
            return new StorageInfo(totalBytes, totalBytes - availableBytes);
        } catch (Exception e) {
            return new StorageInfo(0, 0);
        }
    }
    
    public static class StorageInfo {
        private final long totalBytes;
        private final long usedBytes;
        
        public StorageInfo(long totalBytes, long usedBytes) {
            this.totalBytes = totalBytes;
            this.usedBytes = usedBytes;
        }
        
        public long getTotalBytes() {
            return totalBytes;
        }
        
        public long getUsedBytes() {
            return usedBytes;
        }
        
        public long getFreeBytes() {
            return totalBytes - usedBytes;
        }
        
        public String getFormattedTotal() {
            return formatBytes(totalBytes);
        }
        
        public String getFormattedUsed() {
            return formatBytes(usedBytes);
        }
        
        public String getFormattedFree() {
            return formatBytes(getFreeBytes());
        }
        
        private String formatBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.2f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
}

