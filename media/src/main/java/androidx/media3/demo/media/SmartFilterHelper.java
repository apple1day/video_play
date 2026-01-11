package androidx.media3.demo.media;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能文件筛选辅助类
 */
public class SmartFilterHelper {
    
    /**
     * 筛选条件枚举
     */
    public enum FilterType {
        ALL("全部"),
        LARGE_FILE("大于1GB"),
        MEDIUM_FILE("500MB-1GB"),
        SMALL_FILE("小于500MB"),
        RECENT_WEEK("最近一周"),
        RECENT_MONTH("最近一月"),
        OLD_FILE("一月前"),
        LONG_VIDEO("超过1小时"),
        MEDIUM_VIDEO("30分钟-1小时"),
        SHORT_VIDEO("少于30分钟");
        
        private final String label;
        
        FilterType(String label) {
            this.label = label;
        }
        
        public String getLabel() {
            return label;
        }
    }
    
    /**
     * 根据文件大小筛选
     */
    public static List<FileItem> filterBySize(List<FileItem> files, FilterType type) {
        List<FileItem> result = new ArrayList<>();
        
        for (FileItem item : files) {
            long size = item.getSize();
            boolean match = false;
            
            switch (type) {
                case LARGE_FILE:
                    match = size > 1024 * 1024 * 1024L; // > 1GB
                    break;
                case MEDIUM_FILE:
                    match = size >= 500 * 1024 * 1024L && size <= 1024 * 1024 * 1024L; // 500MB-1GB
                    break;
                case SMALL_FILE:
                    match = size < 500 * 1024 * 1024L; // < 500MB
                    break;
                default:
                    match = true;
            }
            
            if (match) {
                result.add(item);
            }
        }
        
        return result;
    }
    
    /**
     * 根据时间筛选
     */
    public static List<FileItem> filterByTime(List<FileItem> files, FilterType type) {
        List<FileItem> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        long weekAgo = now - 7 * 24 * 60 * 60 * 1000L;
        long monthAgo = now - 30 * 24 * 60 * 60 * 1000L;
        
        for (FileItem item : files) {
            long modifiedTime = item.getLastModified();
            boolean match = false;
            
            switch (type) {
                case RECENT_WEEK:
                    match = modifiedTime >= weekAgo;
                    break;
                case RECENT_MONTH:
                    match = modifiedTime >= monthAgo && modifiedTime < weekAgo;
                    break;
                case OLD_FILE:
                    match = modifiedTime < monthAgo;
                    break;
                default:
                    match = true;
            }
            
            if (match) {
                result.add(item);
            }
        }
        
        return result;
    }
    
    /**
     * 根据视频时长筛选
     * 注意：这需要从文件中读取实际时长，可能较慢
     */
    public static List<FileItem> filterByDuration(List<FileItem> files, FilterType type) {
        List<FileItem> result = new ArrayList<>();
        
        for (FileItem item : files) {
            // 获取视频时长
            long duration = ThumbnailLoader.getVideoDuration(item.getFile().getAbsolutePath());
            boolean match = false;
            
            if (duration > 0) {
                long durationMinutes = duration / 60000;
                
                switch (type) {
                    case LONG_VIDEO:
                        match = durationMinutes > 60; // > 1小时
                        break;
                    case MEDIUM_VIDEO:
                        match = durationMinutes >= 30 && durationMinutes <= 60; // 30分钟-1小时
                        break;
                    case SHORT_VIDEO:
                        match = durationMinutes < 30; // < 30分钟
                        break;
                    default:
                        match = true;
                }
            }
            
            if (match) {
                result.add(item);
            }
        }
        
        return result;
    }
    
    /**
     * 智能筛选（组合条件）
     */
    public static List<FileItem> smartFilter(List<FileItem> files, FilterType... types) {
        List<FileItem> result = new ArrayList<>(files);
        
        for (FilterType type : types) {
            switch (type) {
                case LARGE_FILE:
                case MEDIUM_FILE:
                case SMALL_FILE:
                    result = filterBySize(result, type);
                    break;
                case RECENT_WEEK:
                case RECENT_MONTH:
                case OLD_FILE:
                    result = filterByTime(result, type);
                    break;
                case LONG_VIDEO:
                case MEDIUM_VIDEO:
                case SHORT_VIDEO:
                    result = filterByDuration(result, type);
                    break;
            }
        }
        
        return result;
    }
    
    /**
     * 获取所有筛选选项
     */
    public static String[] getAllFilterLabels() {
        FilterType[] types = FilterType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getLabel();
        }
        return labels;
    }
    
    /**
     * 获取分类筛选选项
     */
    public static String[] getSizeFilterLabels() {
        return new String[]{
            FilterType.ALL.getLabel(),
            FilterType.LARGE_FILE.getLabel(),
            FilterType.MEDIUM_FILE.getLabel(),
            FilterType.SMALL_FILE.getLabel()
        };
    }
    
    public static String[] getTimeFilterLabels() {
        return new String[]{
            FilterType.ALL.getLabel(),
            FilterType.RECENT_WEEK.getLabel(),
            FilterType.RECENT_MONTH.getLabel(),
            FilterType.OLD_FILE.getLabel()
        };
    }
    
    public static String[] getDurationFilterLabels() {
        return new String[]{
            FilterType.ALL.getLabel(),
            FilterType.LONG_VIDEO.getLabel(),
            FilterType.MEDIUM_VIDEO.getLabel(),
            FilterType.SHORT_VIDEO.getLabel()
        };
    }
}
