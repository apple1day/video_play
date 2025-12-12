package androidx.media3.demo.filebrowser;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView emptyView;
    private Button sortTimeButton;
    private Button sortSizeButton;
    private Button viewToggleButton;
    private FileAdapter adapter;
    private List<FileItem> allFiles = new ArrayList<>();
    private List<FileItem> allVideoFiles = new ArrayList<>(); // 存储所有视频文件
    private FileItem.FileType currentFilter = FileItem.FileType.VIDEO;
    private SortType currentSort = SortType.TIME;
    private boolean timeSortDescending = true; // 默认从新到旧（降序）
    private boolean sizeSortDescending = true; // 默认从大到小（降序）
    private boolean isGridView = false; // 默认列表视图

    private enum SortType {
        TIME, SIZE
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        sortTimeButton = view.findViewById(R.id.sort_time_button);
        sortSizeButton = view.findViewById(R.id.sort_size_button);
        viewToggleButton = view.findViewById(R.id.view_toggle_button);

        adapter = new FileAdapter(new ArrayList<>(), fileItem -> {
            if (fileItem.getFileType() == FileItem.FileType.VIDEO) {
                Intent intent = new Intent(getContext(), PlayerActivity.class);
                String currentPath = fileItem.getFile().getAbsolutePath();
                intent.putExtra("file_path", currentPath);
                // 传递所有视频文件列表，而不是当前筛选后的列表
                intent.putExtra("file_list", getAllVideoFileList());
                // 找到当前视频在所有视频列表中的索引
                int index = findVideoIndexInAllList(currentPath);
                intent.putExtra("current_index", index);
                startActivity(intent);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        setupSortButtons();
        setupViewToggleButton();

        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadFiles();
        }

        return view;
    }


    private void setupSortButtons() {
        // 设置时间排序按钮
        updateSortTimeButtonIcon();
        sortTimeButton.setOnClickListener(v -> {
            currentSort = SortType.TIME;
            timeSortDescending = !timeSortDescending;
            updateSortTimeButtonIcon();
            applyFilterAndSort();
        });

        // 设置大小排序按钮
        updateSortSizeButtonIcon();
        sortSizeButton.setOnClickListener(v -> {
            currentSort = SortType.SIZE;
            sizeSortDescending = !sizeSortDescending;
            updateSortSizeButtonIcon();
            applyFilterAndSort();
        });
    }

    private void updateSortTimeButtonIcon() {
        int iconRes = timeSortDescending 
            ? android.R.drawable.arrow_down_float 
            : android.R.drawable.arrow_up_float;
        sortTimeButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes, 0);
    }

    private void updateSortSizeButtonIcon() {
        int iconRes = sizeSortDescending 
            ? android.R.drawable.arrow_down_float 
            : android.R.drawable.arrow_up_float;
        sortSizeButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes, 0);
    }

    private void setupViewToggleButton() {
        updateViewToggleButton();
        viewToggleButton.setOnClickListener(v -> {
            isGridView = !isGridView;
            updateViewToggleButton();
            switchLayoutManager();
        });
    }

    private void updateViewToggleButton() {
        if (isGridView) {
            // 当前是网格视图，点击后切换到列表视图，所以显示列表视图按钮
            viewToggleButton.setText(getString(R.string.list_view));
            // 使用列表图标（左边）
            viewToggleButton.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_sort_alphabetically, 0, 0, 0);
        } else {
            // 当前是列表视图，点击后切换到网格视图，所以显示网格视图按钮
            viewToggleButton.setText(getString(R.string.grid_view));
            // 使用网格图标（左边）
            viewToggleButton.setCompoundDrawablesWithIntrinsicBounds(
                android.R.drawable.ic_menu_view, 0, 0, 0);
        }
    }

    private void switchLayoutManager() {
        if (isGridView) {
            // 切换到网格视图
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
            recyclerView.setLayoutManager(gridLayoutManager);
        } else {
            // 切换到列表视图
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
            recyclerView.setLayoutManager(linearLayoutManager);
        }
    }

    public void refreshFiles() {
        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadFiles();
        }
    }

    private void loadFiles() {
        new Thread(() -> {
            List<File> storageDirectories = FileUtils.getStorageDirectories(getContext());
            // 先扫描所有视频文件，用于播放列表
            List<FileItem> allVideos = FileUtils.scanAllFiles(storageDirectories, FileItem.FileType.VIDEO);
            // 对视频列表按时间排序（用于播放顺序）
            Collections.sort(allVideos, (o1, o2) -> Long.compare(o2.getLastModified(), o1.getLastModified()));
            
            // 再根据当前筛选条件扫描文件，用于显示
            List<FileItem> files = FileUtils.scanAllFiles(storageDirectories, currentFilter);
            
            new Handler(Looper.getMainLooper()).post(() -> {
                allVideoFiles = allVideos; // 保存所有视频文件（已排序）
                allFiles = files; // 保存当前筛选的文件
                applyFilterAndSort();
            });
        }).start();
    }

    private void applyFilterAndSort() {
        List<FileItem> filtered = new ArrayList<>();
        for (FileItem item : allFiles) {
            if (currentFilter == null || item.getFileType() == currentFilter) {
                filtered.add(item);
            }
        }

        Collections.sort(filtered, (o1, o2) -> {
            if (currentSort == SortType.TIME) {
                if (timeSortDescending) {
                    return Long.compare(o2.getLastModified(), o1.getLastModified());
                } else {
                    return Long.compare(o1.getLastModified(), o2.getLastModified());
                }
            } else {
                if (sizeSortDescending) {
                    return Long.compare(o2.getSize(), o1.getSize());
                } else {
                    return Long.compare(o1.getSize(), o2.getSize());
                }
            }
        });

        adapter.updateFiles(filtered);
        
        if (filtered.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 获取所有视频文件的路径列表（用于播放器）
     * 返回所有查询到的视频，不管当前筛选条件
     */
    private ArrayList<String> getAllVideoFileList() {
        ArrayList<String> fileList = new ArrayList<>();
        for (FileItem item : allVideoFiles) {
            fileList.add(item.getFile().getAbsolutePath());
        }
        return fileList;
    }
    
    /**
     * 在所有视频列表中找到指定路径的索引
     */
    private int findVideoIndexInAllList(String filePath) {
        for (int i = 0; i < allVideoFiles.size(); i++) {
            if (allVideoFiles.get(i).getFile().getAbsolutePath().equals(filePath)) {
                return i;
            }
        }
        return 0; // 如果找不到，返回 0
    }
}

