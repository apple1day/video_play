package androidx.media3.demo.media;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
    private LinearLayout storageInfoLayout;
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
        storageInfoLayout = view.findViewById(R.id.storage_info_layout);
        pathTextView = view.findViewById(R.id.path_text);
        backButton = view.findViewById(R.id.back_button);
        settingsButton = view.findViewById(R.id.settings_button);

        adapter = new FileBrowserAdapter(new ArrayList<>(), item -> {
            if (item.isDirectory()) {
                navigateToDirectory(item.getFile());
            } else {
                // 可以在这里添加文件点击处理，比如打开文件
                Toast.makeText(getContext(), "文件: " + item.getName(), Toast.LENGTH_SHORT).show();
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
            loadStorageInfo();
            loadRootDirectories();
        } else {
            showEmptyMessage(getString(R.string.permission_required));
        }

        return view;
    }

    public void refreshFiles() {
        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadStorageInfo();
            if (currentDirectory != null) {
                navigateToDirectory(currentDirectory);
            } else {
                loadRootDirectories();
            }
        }
    }

    private void loadStorageInfo() {
        new Thread(() -> {
            storageRoots = FileUtils.getStorageDirectories(getContext());
            List<FileUtils.StorageInfo> storageInfos = new ArrayList<>();
            
            for (File directory : storageRoots) {
                storageInfos.add(FileUtils.getStorageInfo(directory));
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                storageInfoLayout.removeAllViews();
                
                for (int i = 0; i < storageRoots.size(); i++) {
                    File directory = storageRoots.get(i);
                    FileUtils.StorageInfo info = storageInfos.get(i);
                    
                    String title = getStorageTitle(directory);
                    View infoView = createStorageInfoView(title, info, directory);
                    storageInfoLayout.addView(infoView);
                }
            });
        }).start();
    }

    private String getStorageTitle(File directory) {
        // 判断是内部存储还是外部存储
        File dataDir = Environment.getDataDirectory();
        File externalStorage = Environment.getExternalStorageDirectory();
        
        if (directory.equals(dataDir)) {
            return getString(R.string.internal_storage);
        } else if (directory.equals(externalStorage)) {
            return getString(R.string.external_storage);
        } else {
            // 可能是U盘或其他外置存储
            String path = directory.getAbsolutePath();
            if (path.contains("/storage/")) {
                // 提取存储设备名称
                String[] parts = path.split("/");
                if (parts.length > 2) {
                    return "外置存储: " + parts[parts.length - 1];
                }
            }
            return "存储设备: " + directory.getName();
        }
    }

    private View createStorageInfoView(String title, FileUtils.StorageInfo info, File directory) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        layout.setPadding(16, 16, 16, 16);
        
        // 设置点击事件，点击存储信息可以进入该存储根目录
        layout.setOnClickListener(v -> {
            navigateToDirectory(directory);
        });
        layout.setClickable(true);
        layout.setFocusable(true);
        
        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 8);
        layout.addView(titleView);
        
        TextView pathView = new TextView(getContext());
        pathView.setText("路径: " + directory.getAbsolutePath());
        pathView.setTextSize(12);
        pathView.setTextColor(0xFF666666);
        pathView.setPadding(0, 0, 0, 8);
        layout.addView(pathView);
        
        TextView totalView = new TextView(getContext());
        totalView.setText(getString(R.string.total_space) + ": " + info.getFormattedTotal());
        totalView.setTextSize(14);
        totalView.setPadding(0, 0, 0, 4);
        layout.addView(totalView);
        
        TextView usedView = new TextView(getContext());
        usedView.setText(getString(R.string.used_space) + ": " + info.getFormattedUsed());
        usedView.setTextSize(14);
        usedView.setPadding(0, 0, 0, 4);
        layout.addView(usedView);
        
        TextView freeView = new TextView(getContext());
        freeView.setText(getString(R.string.free_space) + ": " + info.getFormattedFree());
        freeView.setTextSize(14);
        layout.addView(freeView);
        
        // 添加边距
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        layout.setLayoutParams(params);
        
        return layout;
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

