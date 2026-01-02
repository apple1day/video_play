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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_more, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view);
        pathTextView = view.findViewById(R.id.path_text);
        backButton = view.findViewById(R.id.back_button);
        settingsButton = view.findViewById(R.id.settings_button);

        adapter = new FileBrowserAdapter(new ArrayList<>(), item -> {
            if (item.isDirectory()) {
                navigateToDirectory(item.getFile());
            } else {
                // 处理文件点击
                handleFileClick(item);
            }
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

