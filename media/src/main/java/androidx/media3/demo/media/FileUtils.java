package androidx.media3.demo.media;

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
        
        // 1. 内部存储（手机本机存储）- 通常是 /storage/emulated/0
        File primaryStorage = Environment.getExternalStorageDirectory();
        if (primaryStorage != null && primaryStorage.exists()) {
            directories.add(primaryStorage);
        }
        
        // 2. 外部存储（U盘、SD卡等可移动存储设备）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directories.addAll(getRemovableStorageDirectoriesApi30(context));
        } else {
            // Android 10 及以下
            directories.addAll(getRemovableStorageDirectories(context));
        }
        
        return directories;
    }
    
    @RequiresApi(api = Build.VERSION_CODES.R)
    private static List<File> getRemovableStorageDirectoriesApi30(Context context) {
        List<File> directories = new ArrayList<>();
        StorageManager storageManager = context.getSystemService(StorageManager.class);
        File primaryStorage = Environment.getExternalStorageDirectory();
        
        if (storageManager != null) {
            List<StorageVolume> volumes = storageManager.getStorageVolumes();
            for (StorageVolume volume : volumes) {
                // 只添加已挂载的、非主存储的卷（即外部设备）
                if (volume.getState().equals(Environment.MEDIA_MOUNTED) && !volume.isPrimary()) {
                    File directory = volume.getDirectory();
                    if (directory != null && directory.exists() && !directory.equals(primaryStorage)) {
                        directories.add(directory);
                    }
                }
            }
        }
        return directories;
    }
    
    /**
     * 获取可移动存储设备（U盘、SD卡等）的目录
     * 适用于 Android 10 及以下版本
     */
    private static List<File> getRemovableStorageDirectories(Context context) {
        List<File> directories = new ArrayList<>();
        File primaryStorage = Environment.getExternalStorageDirectory();
        
        // 常见的可移动存储路径
        File storageDir = new File("/storage");
        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] files = storageDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    // 排除主存储（emulated）和系统路径
                    if (!name.equals("emulated") && !name.equals("self") && 
                        !name.equals("sdcard") && file.isDirectory()) {
                        // 检查是否是挂载的存储设备且不是主存储
                        if (file.exists() && file.canRead() && !file.equals(primaryStorage)) {
                            File[] subFiles = file.listFiles();
                            if (subFiles != null && subFiles.length > 0) {
                                directories.add(file);
                            }
                        }
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
    
    /**
     * 判断指定目录是否是主存储（手机本机存储）
     */
    public static boolean isPrimaryStorage(File directory) {
        File primaryStorage = Environment.getExternalStorageDirectory();
        return directory.equals(primaryStorage);
    }
    
    /**
     * 判断指定目录是否是外部存储设备（U盘、SD卡等）
     */
    public static boolean isRemovableStorage(File directory) {
        return !isPrimaryStorage(directory);
    }
    
    /**
     * 获取存储设备的显示名称
     */
    public static String getStorageDisplayName(File directory) {
        if (isPrimaryStorage(directory)) {
            return "内部存储";  // 手机本机存储
        } else {
            String path = directory.getAbsolutePath();
            String name = directory.getName();
            
            // 识别常见的外部设备类型
            if (name.toUpperCase().contains("USB") || path.contains("usb")) {
                return "U盘";
            } else if (name.toLowerCase().contains("sdcard") || name.toUpperCase().contains("SD")) {
                return "SD卡";
            } else {
                return "外部存储";
            }
        }
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

