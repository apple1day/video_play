package androidx.media3.demo.media;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义 DocumentsProvider
 * 实现类似 iOS 文件共享功能，让电脑可以通过 USB 访问应用文件
 * 
 * 使用方式：
 * 1. 在 Windows 上通过"文件资源管理器"访问 Android 设备
 * 2. 在 Mac 上通过 Android File Transfer 访问
 * 3. 在 Linux 上通过 MTP 协议访问
 */
public class DocumentsProvider extends android.provider.DocumentsProvider {

    private static final String TAG = "MediaDocumentsProvider";

    // 根目录 ID
    private static final String ROOT_ID = "media_root";
    
    // 默认的根列
    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
    };

    // 默认的文档列
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
    };

    @Override
    public boolean onCreate() {
        Log.d(TAG, "DocumentsProvider 已创建");
        return true;
    }

    /**
     * 返回应用的根目录信息
     * 类似 iOS 的 UIFileSharingEnabled 功能
     */
    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        Log.d(TAG, "queryRoots 被调用");
        
        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_ROOT_PROJECTION);

        Context context = getContext();
        if (context == null) {
            return result;
        }

        // 添加应用根目录
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(Root.COLUMN_DOCUMENT_ID, getDocumentId(getBaseDir()));
        row.add(Root.COLUMN_TITLE, context.getString(R.string.app_name));
        row.add(Root.COLUMN_SUMMARY, "视频播放器文件");
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);
        
        // 设置标志：支持创建文档
        int flags = Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD;
        row.add(Root.COLUMN_FLAGS, flags);
        
        // 支持的 MIME 类型
        row.add(Root.COLUMN_MIME_TYPES, "video/*\naudio/*\nimage/*");
        
        // 可用空间
        File baseDir = getBaseDir();
        if (baseDir != null) {
            row.add(Root.COLUMN_AVAILABLE_BYTES, baseDir.getFreeSpace());
        }

        return result;
    }

    /**
     * 查询文档（文件或目录）信息
     */
    @Override
    public Cursor queryDocument(String documentId, String[] projection) 
            throws FileNotFoundException {
        Log.d(TAG, "queryDocument: " + documentId);
        
        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        
        File file = getFileForDocId(documentId);
        includeFile(result, file);
        
        return result;
    }

    /**
     * 查询子文档（目录中的文件列表）
     */
    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                      String sortOrder) throws FileNotFoundException {
        Log.d(TAG, "queryChildDocuments: " + parentDocumentId);
        
        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        
        File parent = getFileForDocId(parentDocumentId);
        
        // 列出所有文件和子目录
        File[] files = parent.listFiles();
        if (files != null) {
            // 排序：目录优先，然后按名称
            List<File> fileList = new ArrayList<>();
            Collections.addAll(fileList, files);
            Collections.sort(fileList, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) {
                    return -1;
                } else if (!f1.isDirectory() && f2.isDirectory()) {
                    return 1;
                } else {
                    return f1.getName().compareToIgnoreCase(f2.getName());
                }
            });
            
            // 添加到结果中
            for (File file : fileList) {
                // 跳过隐藏文件
                if (!file.getName().startsWith(".")) {
                    includeFile(result, file);
                }
            }
        }
        
        return result;
    }

    /**
     * 打开文档进行读取
     */
    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
                                             @Nullable CancellationSignal signal)
            throws FileNotFoundException {
        Log.d(TAG, "openDocument: " + documentId + ", mode: " + mode);
        
        File file = getFileForDocId(documentId);
        
        // 检查文件是否存在
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + file.getPath());
        }
        
        // 根据模式打开文件
        int accessMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, accessMode);
    }

    /**
     * 创建新文档
     */
    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        Log.d(TAG, "createDocument: " + displayName + ", type: " + mimeType);
        
        File parent = getFileForDocId(parentDocumentId);
        File file = new File(parent, displayName);
        
        try {
            // 创建文件
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (!file.mkdir()) {
                    throw new FileNotFoundException("无法创建目录: " + file.getPath());
                }
            } else {
                if (!file.createNewFile()) {
                    throw new FileNotFoundException("无法创建文件: " + file.getPath());
                }
            }
        } catch (IOException e) {
            throw new FileNotFoundException("创建文件失败: " + e.getMessage());
        }
        
        return getDocumentId(file);
    }

    /**
     * 删除文档
     */
    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        Log.d(TAG, "deleteDocument: " + documentId);
        
        File file = getFileForDocId(documentId);
        
        if (!file.delete()) {
            throw new FileNotFoundException("无法删除文件: " + file.getPath());
        }
    }

    /**
     * 重命名文档
     */
    @Override
    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        Log.d(TAG, "renameDocument: " + documentId + " -> " + displayName);
        
        File file = getFileForDocId(documentId);
        File newFile = new File(file.getParentFile(), displayName);
        
        if (!file.renameTo(newFile)) {
            throw new FileNotFoundException("无法重命名文件");
        }
        
        return getDocumentId(newFile);
    }

    /**
     * 获取基础目录（应用的外部文件目录）
     */
    private File getBaseDir() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        
        // 使用外部存储的应用专属目录
        // 相当于 iOS 的 Documents 目录
        return context.getExternalFilesDir(null);
    }

    /**
     * 根据文档 ID 获取文件
     */
    private File getFileForDocId(String documentId) throws FileNotFoundException {
        File baseDir = getBaseDir();
        if (baseDir == null) {
            throw new FileNotFoundException("基础目录不可用");
        }
        
        // ROOT 特殊处理
        if (ROOT_ID.equals(documentId)) {
            return baseDir;
        }
        
        // 解码路径
        File file = new File(baseDir, documentId);
        
        // 安全检查：确保文件在基础目录内
        try {
            String basePath = baseDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            
            if (!filePath.startsWith(basePath)) {
                throw new FileNotFoundException("非法的文件路径");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("路径解析失败");
        }
        
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + file.getPath());
        }
        
        return file;
    }

    /**
     * 获取文件的文档 ID
     */
    private String getDocumentId(File file) {
        File baseDir = getBaseDir();
        if (baseDir == null || file == null) {
            return ROOT_ID;
        }
        
        try {
            String basePath = baseDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            
            if (filePath.equals(basePath)) {
                return ROOT_ID;
            }
            
            // 返回相对路径
            return filePath.substring(basePath.length() + 1);
        } catch (IOException e) {
            Log.e(TAG, "获取文档 ID 失败", e);
            return ROOT_ID;
        }
    }

    /**
     * 将文件信息添加到结果游标中
     */
    private void includeFile(MatrixCursor result, File file) {
        final MatrixCursor.RowBuilder row = result.newRow();
        
        // 文档 ID
        row.add(Document.COLUMN_DOCUMENT_ID, getDocumentId(file));
        
        // 显示名称
        row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
        
        // MIME 类型
        String mimeType;
        if (file.isDirectory()) {
            mimeType = Document.MIME_TYPE_DIR;
        } else {
            mimeType = getMimeType(file);
        }
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        
        // 修改时间
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        
        // 文件大小
        row.add(Document.COLUMN_SIZE, file.length());
        
        // 标志
        int flags = 0;
        if (file.isDirectory()) {
            // 目录支持创建子文档
            flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        }
        if (file.canWrite()) {
            // 可写文件支持删除和重命名
            flags |= Document.FLAG_SUPPORTS_DELETE;
            flags |= Document.FLAG_SUPPORTS_RENAME;
            flags |= Document.FLAG_SUPPORTS_WRITE;
        }
        // 支持缩略图（视频和图片）
        if (isImageOrVideo(mimeType)) {
            flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
        }
        row.add(Document.COLUMN_FLAGS, flags);
    }

    /**
     * 获取文件的 MIME 类型
     */
    private String getMimeType(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            String extension = name.substring(lastDot + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    /**
     * 判断是否是图片或视频
     */
    private boolean isImageOrVideo(String mimeType) {
        return mimeType != null && 
               (mimeType.startsWith("image/") || mimeType.startsWith("video/"));
    }

    /**
     * 打开文档缩略图
     */
    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint,
                                                     CancellationSignal signal)
            throws FileNotFoundException {
        // 这里可以实现视频缩略图生成
        // 目前返回原文件
        File file = getFileForDocId(documentId);
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, file.length());
    }
}
