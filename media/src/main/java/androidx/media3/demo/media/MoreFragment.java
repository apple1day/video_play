package androidx.media3.demo.media;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MoreFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView pathTextView;
    private View backButton;
    private View settingsButton;
    private FileBrowserAdapter adapter;
    private File currentDirectory;
    private List<File> directoryHistory = new ArrayList<>();
    private List<File> storageRoots = new ArrayList<>();
    private FavoriteManager favoriteManager;
    private PendingDeleteManager pendingDeleteManager;
    private PendingDeleteManager.OnFileDeletedListener pendingDeleteListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_more, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view);
        pathTextView = view.findViewById(R.id.path_text);
        backButton = view.findViewById(R.id.back_button);
        settingsButton = view.findViewById(R.id.settings_button);

        // 初始化管理器
        favoriteManager = FavoriteManager.getInstance(getContext());
        pendingDeleteManager = PendingDeleteManager.getInstance(getContext());
        
        // 设置文件删除监听器（使用 add 方式，不覆盖其他监听器）
        pendingDeleteListener = new PendingDeleteManager.OnFileDeletedListener() {
            @Override
            public void onFileDeleted(String filePath, boolean success, String errorMessage) {
                // 文件删除后刷新列表
                if (success && getActivity() != null) {
                    getActivity().runOnUiThread(() -> refreshCurrentDirectory());
                }
            }
            
            @Override
            public void onFilesDeleted(int successCount, int failCount, List<String> failedFiles) {
                // 批量删除后刷新列表
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> refreshCurrentDirectory());
                }
            }
        };
        pendingDeleteManager.addOnFileDeletedListener(pendingDeleteListener);

        adapter = new FileBrowserAdapter(new ArrayList<>(), item -> {
            if (item.isDirectory()) {
                navigateToDirectory(item.getFile());
            } else {
                // 处理文件点击
                handleFileClick(item);
            }
        }, favoriteManager, pendingDeleteManager);
        
        // 设置长按监听器
        adapter.setOnItemLongClickListener(item -> {
            if (!item.isDirectory()) {
                showFileOptionsDialog(item);
            }
            return true;
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // 设置返回按钮点击事件
        if (backButton != null) {
            backButton.setOnClickListener(v -> navigateBack());
        }

        // 设置按钮点击事件
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), SettingsActivity.class);
                startActivity(intent);
            });
        }

        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadRootDirectories();
        } else {
            showEmptyMessage(getString(R.string.permission_required));
        }

        return view;
    }
    
    /**
     * 处理文件点击事件
     */
    private void handleFileClick(FileItem item) {
        File file = item.getFile();
        
        if (item.getFileType() == FileItem.FileType.VIDEO) {
            // 视频文件：播放
            Intent intent = new Intent(getContext(), PlayerActivity.class);
            intent.putExtra("file_path", file.getAbsolutePath());
            
            // 获取当前目录下所有视频文件
            ArrayList<String> videoList = new ArrayList<>();
            List<FileItem> items = adapter.getFiles();
            for (FileItem i : items) {
                if (i.getFileType() == FileItem.FileType.VIDEO) {
                    videoList.add(i.getFile().getAbsolutePath());
                }
            }
            intent.putExtra("file_list", videoList);
            
            // 找到当前视频在列表中的索引
            int index = videoList.indexOf(file.getAbsolutePath());
            intent.putExtra("current_index", index >= 0 ? index : 0);
            
            startActivity(intent);
        } else if (isImageFile(file)) {
            // 图片文件：预览
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.fromFile(file);
            intent.setDataAndType(uri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(getContext(), "无法打开图片", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 其他文件：显示文件信息
            showFileInfo(item);
        }
    }
    
    /**
     * 判断是否是图片文件
     */
    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
               name.endsWith(".png") || name.endsWith(".gif") || 
               name.endsWith(".bmp") || name.endsWith(".webp");
    }
    
    /**
     * 显示文件信息
     */
    private void showFileInfo(FileItem item) {
        String info = "文件名: " + item.getName() + "\n" +
                     "大小: " + item.getFormattedSize() + "\n" +
                     "类型: " + getFileTypeString(item.getFileType()) + "\n" +
                     "路径: " + item.getFile().getAbsolutePath();
        Toast.makeText(getContext(), info, Toast.LENGTH_LONG).show();
    }
    
    /**
     * 显示文件选项对话框
     */
    private void showFileOptionsDialog(FileItem item) {
        if (getContext() == null) return;
        
        File file = item.getFile();
        String filePath = file.getAbsolutePath();
        
        boolean isFavorite = favoriteManager.isFavorite(filePath);
        boolean isPendingDelete = pendingDeleteManager.isPendingDelete(filePath);
        
        // 创建选项列表
        List<String> options = new ArrayList<>();
        options.add(isFavorite ? "取消收藏" : "收藏");
        options.add(isPendingDelete ? "取消待删除" : "标记待删除");
        options.add("立即删除");
        options.add("查看信息");
        
        new AlertDialog.Builder(getContext())
            .setTitle(item.getName())
            .setItems(options.toArray(new String[0]), (dialog, which) -> {
                switch (which) {
                    case 0: // 收藏/取消收藏
                        toggleFavorite(filePath);
                        break;
                    case 1: // 标记待删除/取消待删除
                        togglePendingDelete(filePath);
                        break;
                    case 2: // 立即删除
                        confirmAndDeleteFile(item);
                        break;
                    case 3: // 查看信息
                        showFileInfo(item);
                        break;
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 切换收藏状态
     */
    private void toggleFavorite(String filePath) {
        boolean isFavorited = favoriteManager.toggleFavorite(filePath);
        String message = isFavorited ? "已收藏" : "已取消收藏";
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        adapter.notifyDataSetChanged();
    }
    
    /**
     * 切换待删除状态
     */
    private void togglePendingDelete(String filePath) {
        boolean isPending = pendingDeleteManager.togglePendingDelete(filePath);
        String message = isPending ? "已标记为待删除" : "已取消待删除";
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        adapter.notifyDataSetChanged();
    }
    
    /**
     * 确认并删除文件
     */
    private void confirmAndDeleteFile(FileItem item) {
        if (getContext() == null) return;
        
        new AlertDialog.Builder(getContext())
            .setTitle("确认删除")
            .setMessage("确定要删除 \"" + item.getName() + "\" 吗？\n此操作无法撤销。")
            .setPositiveButton("删除", (dialog, which) -> deleteFileAsync(item))
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 异步删除文件
     */
    private void deleteFileAsync(FileItem item) {
        if (getContext() == null) return;
        
        File file = item.getFile();
        String fileName = file.getName();
        
        // 显示删除中提示
        Toast.makeText(getContext(), "正在删除 " + fileName + "...", Toast.LENGTH_SHORT).show();
        
        // 在后台线程删除文件
        new Thread(() -> {
            boolean success = false;
            String errorMessage = null;
            
            try {
                if (file.exists()) {
                    if (!file.canWrite()) {
                        errorMessage = "文件无写权限";
                    } else if (file.getParentFile() != null && !file.getParentFile().canWrite()) {
                        errorMessage = "父目录无写权限";
                    } else {
                        success = file.delete();
                        if (success) {
                            // 等待一小段时间，让系统完成删除操作
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                // 忽略
                            }
                            
                            // 检查文件是否真的被删除了
                            if (file.exists()) {
                                // 文件仍然存在，可能是0字节占位文件
                                // 再次尝试删除
                                success = file.delete();
                                
                                // 再次检查
                                if (file.exists()) {
                                    // 如果是0字节文件，也认为删除成功
                                    if (file.length() == 0) {
                                        success = true;
                                    } else {
                                        success = false;
                                        errorMessage = "删除失败（文件仍然存在）";
                                    }
                                }
                            }
                            
                            // 通知媒体扫描器更新
                            if (success && getContext() != null) {
                                try {
                                    android.media.MediaScannerConnection.scanFile(
                                        getContext(),
                                        new String[]{file.getAbsolutePath()},
                                        null,
                                        null
                                    );
                                } catch (Exception e) {
                                    // 忽略扫描错误
                                }
                            }
                        } else {
                            // 检查文件是否已经不存在了
                            if (!file.exists()) {
                                success = true;
                            } else {
                                errorMessage = "删除失败（可能被占用或系统保护）";
                            }
                        }
                    }
                } else {
                    errorMessage = "文件不存在";
                }
            } catch (SecurityException e) {
                errorMessage = "安全异常：" + e.getMessage();
            } catch (Exception e) {
                errorMessage = "删除异常：" + e.getMessage();
            }
            
            // 在主线程更新UI
            final boolean finalSuccess = success;
            final String finalError = errorMessage;
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (finalSuccess) {
                        Toast.makeText(getContext(), "✓ 已删除: " + fileName, Toast.LENGTH_SHORT).show();
                        // 从收藏和待删除列表中移除
                        favoriteManager.removeFavorite(file.getAbsolutePath());
                        pendingDeleteManager.removePendingDelete(file.getAbsolutePath());
                        // 刷新列表
                        refreshCurrentDirectory();
                    } else {
                        Toast.makeText(getContext(), 
                            "✗ 删除失败: " + fileName + "\n" + finalError, 
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }
    
    /**
     * 获取文件类型字符串
     */
    private String getFileTypeString(FileItem.FileType type) {
        switch (type) {
            case VIDEO: return "视频";
            case AUDIO: return "音频";
            case DOCUMENT: return "文档";
            default: return "其他";
        }
    }

    public void refreshFiles() {
        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            if (currentDirectory != null) {
                navigateToDirectory(currentDirectory);
            } else {
                loadRootDirectories();
            }
        }
    }
    
    /**
     * 刷新当前目录（用于文件删除后）
     */
    private void refreshCurrentDirectory() {
        if (currentDirectory != null) {
            navigateToDirectory(currentDirectory, false);
        } else {
            loadRootDirectories();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 移除监听器
        if (pendingDeleteManager != null && pendingDeleteListener != null) {
            pendingDeleteManager.removeOnFileDeletedListener(pendingDeleteListener);
            pendingDeleteListener = null;
        }
    }

    private void loadRootDirectories() {
        new Thread(() -> {
            storageRoots = FileUtils.getStorageDirectories(getContext());
            List<FileItem> items = new ArrayList<>();
            
            for (File directory : storageRoots) {
                items.add(new FileItem(directory));
            }
            
            // 按名称排序
            Collections.sort(items, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            
            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.updateFiles(items);
                currentDirectory = null;
                directoryHistory.clear();
                updatePathText();
            });
        }).start();
    }

    private void navigateToDirectory(File directory) {
        navigateToDirectory(directory, true);
    }
    
    private void navigateToDirectory(File directory, boolean addToHistory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            Toast.makeText(getContext(), "无法访问该目录", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 添加到历史记录（只有在向前导航时才添加）
        if (addToHistory && currentDirectory != null && !currentDirectory.equals(directory)) {
            directoryHistory.add(currentDirectory);
        }
        
        currentDirectory = directory;
        
        new Thread(() -> {
            List<FileItem> items = new ArrayList<>();
            File[] files = directory.listFiles();
            
            if (files != null) {
                for (File file : files) {
                    if (!file.isHidden()) {
                        items.add(new FileItem(file));
                    }
                }
                
                // 排序：文件夹在前，然后按名称排序
                Collections.sort(items, new Comparator<FileItem>() {
                    @Override
                    public int compare(FileItem a, FileItem b) {
                        if (a.isDirectory() && !b.isDirectory()) {
                            return -1;
                        } else if (!a.isDirectory() && b.isDirectory()) {
                            return 1;
                        } else {
                            return a.getName().compareToIgnoreCase(b.getName());
                        }
                    }
                });
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.updateFiles(items);
                updatePathText();
                
                if (items.isEmpty()) {
                    showEmptyMessage("该目录为空");
                }
            });
        }).start();
    }

    private void navigateBack() {
        if (!directoryHistory.isEmpty()) {
            File previousDirectory = directoryHistory.remove(directoryHistory.size() - 1);
            // 不添加到历史记录，因为这是返回操作
            navigateToDirectory(previousDirectory, false);
        } else if (currentDirectory != null) {
            // 返回根目录
            currentDirectory = null;
            directoryHistory.clear();
            loadRootDirectories();
        }
    }

    private void updatePathText() {
        if (pathTextView != null) {
            if (currentDirectory != null) {
                pathTextView.setText("当前路径: " + currentDirectory.getAbsolutePath());
                pathTextView.setVisibility(View.VISIBLE);
            } else {
                pathTextView.setText("存储根目录");
                pathTextView.setVisibility(View.VISIBLE);
            }
        }
        
        // 更新返回按钮显示状态
        if (backButton != null) {
            if (currentDirectory != null && !directoryHistory.isEmpty()) {
                backButton.setVisibility(View.VISIBLE);
            } else {
                backButton.setVisibility(View.GONE);
            }
        }
    }

    private void showEmptyMessage(String message) {
        // 可以在这里添加空状态显示
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
}

