package androidx.media3.demo.filebrowser;

import java.io.File;

public class FileItem {
    private final File file;
    private final String name;
    private final long size;
    private final long lastModified;
    private final boolean isDirectory;
    private final FileType fileType;

    public enum FileType {
        VIDEO, AUDIO, DOCUMENT, OTHER
    }

    public FileItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.size = file.length();
        this.lastModified = file.lastModified();
        this.isDirectory = file.isDirectory();
        this.fileType = determineFileType(file);
    }

    private FileType determineFileType(File file) {
        if (file.isDirectory()) {
            return FileType.OTHER;
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv") ||
            name.endsWith(".mov") || name.endsWith(".wmv") || name.endsWith(".flv") ||
            name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".m4v")) {
            return FileType.VIDEO;
        } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") ||
                   name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".m4a") ||
                   name.endsWith(".wma")) {
            return FileType.AUDIO;
        } else if (name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") ||
                   name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".ppt") ||
                   name.endsWith(".pptx") || name.endsWith(".txt") || name.endsWith(".rtf")) {
            return FileType.DOCUMENT;
        }
        return FileType.OTHER;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public FileType getFileType() {
        return fileType;
    }

    public String getFormattedSize() {
        if (isDirectory) {
            return "";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}

