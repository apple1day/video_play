package androidx.media3.demo.media;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.SearchView;
import android.widget.TextView;
import android.text.TextUtils;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    
    private SearchView searchView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView loadingView;
    private Spinner storageSpinner;
    private Button sortTimeButton;
    private Button sortSizeButton;
    private Button viewToggleButton;
    private Button smartFilterButton;
    private Button browseAllVideosButton;
    private View toolbarLayout;
    private View selectionToolbarLayout;
    private Button selectAllButton;
    private Button selectFavoritesButton;
    private Button selectPendingDeletesButton;
    private Button deleteSelectedButton;
    private Button cancelSelectionButton;
    private TextView selectedCountText;
    private FileAdapter adapter;
    private List<FileItem> allFiles = new ArrayList<>();
    private List<FileItem> allVideoFiles = new ArrayList<>(); // 存储所有视频文件
    private List<FileItem> displayedFiles = new ArrayList<>(); // 当前显示的视频列表（排序后）
    private String currentSearchQuery = ""; // 当前搜索关键词
    private FileItem.FileType currentFilter = FileItem.FileType.VIDEO;
    private SortType currentSort = SortType.TIME;
    private boolean timeSortDescending = true; // 默认从新到旧（降序）
    private boolean sizeSortDescending = true; // 默认从大到小（降序）
    private boolean isGridView = false; // 默认列表视图
    private ExecutorService deleteExecutor;
    private Handler mainHandler;
    private PendingDeleteManager pendingDeleteManager;
    
    // 存储相关
    private List<File> storageDirectories = new ArrayList<>();
    private boolean isInternalStorage = true; // true=内部存储, false=外部存储
    private boolean hasExternalStorage = false; // 是否有外部存储

    private enum SortType {
        TIME, SIZE
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        searchView = view.findViewById(R.id.search_view);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        loadingView = view.findViewById(R.id.loading_view);
        storageSpinner = view.findViewById(R.id.storage_spinner);
        sortTimeButton = view.findViewById(R.id.sort_time_button);
        sortSizeButton = view.findViewById(R.id.sort_size_button);
        viewToggleButton = view.findViewById(R.id.view_toggle_button);
        smartFilterButton = view.findViewById(R.id.smart_filter_button);
        browseAllVideosButton = view.findViewById(R.id.browse_all_videos_button);
        toolbarLayout = view.findViewById(R.id.toolbar_layout);
        selectionToolbarLayout = view.findViewById(R.id.selection_toolbar_layout);
        selectAllButton = view.findViewById(R.id.select_all_button);
        selectFavoritesButton = view.findViewById(R.id.select_favorites_button);
        selectPendingDeletesButton = view.findViewById(R.id.select_pending_deletes_button);
        deleteSelectedButton = view.findViewById(R.id.delete_selected_button);
        cancelSelectionButton = view.findViewById(R.id.cancel_selection_button);
        selectedCountText = view.findViewById(R.id.selected_count_text);

        adapter = new FileAdapter(new ArrayList<>(), fileItem -> {
            if (!adapter.isSelectionMode() && fileItem.getFileType() == FileItem.FileType.VIDEO) {
                Intent intent = new Intent(getContext(), PlayerActivity.class);
                String currentPath = fileItem.getFile().getAbsolutePath();
                intent.putExtra("file_path", currentPath);
                // 传递当前排序后的视频文件列表
                intent.putExtra("file_list", getCurrentDisplayedVideoList());
                // 找到当前视频在显示列表中的索引
                int index = findVideoIndexInDisplayedList(currentPath);
                intent.putExtra("current_index", index);
                startActivity(intent);
            }
        });
        
        // 设置 context 以初始化 FavoriteManager
        adapter.setContext(getContext());
        
        // 监听选择状态变化
        adapter.setSelectionChangeListener(count -> {
            updateSelectionUI();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        // 设置搜索框
        setupSearchView();
        // 设置下拉刷新
        setupSwipeRefresh();
        setupStorageSpinner();
        setupSortButtons();
        setupViewToggleButton();
        setupSmartFilterButton();
        setupSelectionButtons();
        setupBrowseAllVideosButton();
        
        // 初始化删除线程池和主线程Handler
        deleteExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化待删除管理器并设置监听器
        pendingDeleteManager = PendingDeleteManager.getInstance(getContext());
        pendingDeleteManager.setOnFileDeletedListener(new PendingDeleteManager.OnFileDeletedListener() {
            @Override
            public void onFileDeleted(String filePath, boolean success, String errorMessage) {
                // 单个文件删除后刷新列表
                Log.d(TAG, "onFileDeleted: " + filePath + ", success=" + success + ", error=" + errorMessage);
                refreshFiles();
            }
            
            @Override
            public void onFilesDeleted(int successCount, int failCount, List<String> failedFiles) {
                // 批量删除后刷新列表
                Log.d(TAG, "onFilesDeleted: success=" + successCount + ", fail=" + failCount);
                refreshFiles();
                // Toast提示已在PendingDeleteManager中显示
            }
        });

        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadFiles();
        }

        return view;
    }
    
    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // 提交搜索（可选）
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // 实时搜索
                currentSearchQuery = newText;
                applyFilterAndSort();
                return true;
            }
        });
        
        // 清除搜索时恢复列表
        searchView.setOnCloseListener(() -> {
            currentSearchQuery = "";
            applyFilterAndSort();
            return false;
        });
    }
    
    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );
        
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // 下拉刷新时重新加载文件
            refreshFiles();
        });
    }
    
    private void setupStorageSpinner() {
        // 检查存储设备
        new Thread(() -> {
            storageDirectories = FileUtils.getStorageDirectories(getContext());
            
            // 判断是否有外部存储
            File externalStorage = Environment.getExternalStorageDirectory();
            hasExternalStorage = false;
            for (File dir : storageDirectories) {
                if (!dir.equals(Environment.getDataDirectory()) && dir.exists()) {
                    hasExternalStorage = true;
                    break;
                }
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                List<String> storageOptions = new ArrayList<>();
                storageOptions.add(getString(R.string.storage_internal));
                
                if (hasExternalStorage) {
                    storageOptions.add(getString(R.string.storage_external));
                }
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getContext(),
                    android.R.layout.simple_spinner_item,
                    storageOptions
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                storageSpinner.setAdapter(adapter);
                
                // 设置选择监听器
                storageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        isInternalStorage = (position == 0);
                        loadFiles();
                    }
                    
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // 不做处理
                    }
                });
            });
        }).start();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 从其他页面返回时刷新列表（避免显示已删除的文件）
        if (adapter != null && adapter.getItemCount() > 0) {
            refreshFiles();
        }
    }
    
    private void setupSelectionButtons() {
        selectAllButton.setOnClickListener(v -> {
            int currentCount = adapter.getSelectedCount();
            int totalCount = adapter.getItemCount();
            
            // 检查是否所有项都已选中
            if (currentCount == totalCount && totalCount > 0) {
                // 如果全部已选中，则取消全选
                adapter.clearSelection();
            } else {
                // 否则全选
                adapter.selectAll();
            }
            // updateSelectionUI 会在 listener 回调中自动调用
        });
        
        selectFavoritesButton.setOnClickListener(v -> {
            adapter.selectAllFavorites();
            // updateSelectionUI 会在 listener 回调中自动调用
        });
        
        selectPendingDeletesButton.setOnClickListener(v -> {
            adapter.selectAllPendingDeletes();
            // updateSelectionUI 会在 listener 回调中自动调用
        });
        
        deleteSelectedButton.setOnClickListener(v -> {
            List<FileItem> selectedItems = adapter.getSelectedItems();
            if (!selectedItems.isEmpty()) {
                deleteSelectedFiles(selectedItems);
            }
        });
        
        cancelSelectionButton.setOnClickListener(v -> {
            adapter.setSelectionMode(false);
            // setSelectionMode 会调用 clearSelection，进而触发 listener
        });
    }
    
    private void updateSelectionUI() {
        if (adapter.isSelectionMode()) {
            toolbarLayout.setVisibility(View.GONE);
            selectionToolbarLayout.setVisibility(View.VISIBLE);
            int count = adapter.getSelectedCount();
            int total = adapter.getItemCount();
            selectedCountText.setText(getString(R.string.selected_count, count));
            deleteSelectedButton.setEnabled(count > 0);
            
            // 根据选中状态更新"全选"按钮文本
            if (count == total && total > 0) {
                selectAllButton.setText(R.string.deselect_all);
            } else {
                selectAllButton.setText(R.string.select_all);
            }
        } else {
            toolbarLayout.setVisibility(View.VISIBLE);
            selectionToolbarLayout.setVisibility(View.GONE);
        }
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
    
    private void setupBrowseAllVideosButton() {
        browseAllVideosButton.setOnClickListener(v -> {
            // 检查权限
            if (!PermissionHelper.hasMediaPermissions(getActivity())) {
                PermissionHelper.requestMediaPermissions(getActivity());
                return;
            }
            
            // 打开全部视频页面
            Intent intent = new Intent(getContext(), AllVideosActivity.class);
            startActivity(intent);
        });
    }
    
    private void setupSmartFilterButton() {
        if (smartFilterButton != null) {
            smartFilterButton.setOnClickListener(v -> showSmartFilterDialog());
        }
    }
    
    private void showSmartFilterDialog() {
        String[] filterOptions = new String[]{
            "按大小筛选",
            "按时间筛选",
            "按时长筛选（较慢）",
            "清除筛选"
        };
        
        new AlertDialog.Builder(getContext())
            .setTitle("智能筛选")
            .setItems(filterOptions, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showSizeFilterDialog();
                        break;
                    case 1:
                        showTimeFilterDialog();
                        break;
                    case 2:
                        showDurationFilterDialog();
                        break;
                    case 3:
                        clearFilter();
                        break;
                }
            })
            .show();
    }
    
    private void showSizeFilterDialog() {
        String[] options = SmartFilterHelper.getSizeFilterLabels();
        new AlertDialog.Builder(getContext())
            .setTitle("按文件大小筛选")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    applyFilterAndSort();
                } else {
                    SmartFilterHelper.FilterType[] types = {
                        null,
                        SmartFilterHelper.FilterType.LARGE_FILE,
                        SmartFilterHelper.FilterType.MEDIUM_FILE,
                        SmartFilterHelper.FilterType.SMALL_FILE
                    };
                    applySmartFilter(types[which]);
                }
                Toast.makeText(getContext(), "已筛选: " + options[which], Toast.LENGTH_SHORT).show();
            })
            .show();
    }
    
    private void showTimeFilterDialog() {
        String[] options = SmartFilterHelper.getTimeFilterLabels();
        new AlertDialog.Builder(getContext())
            .setTitle("按时间筛选")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    applyFilterAndSort();
                } else {
                    SmartFilterHelper.FilterType[] types = {
                        null,
                        SmartFilterHelper.FilterType.RECENT_WEEK,
                        SmartFilterHelper.FilterType.RECENT_MONTH,
                        SmartFilterHelper.FilterType.OLD_FILE
                    };
                    applySmartFilter(types[which]);
                }
                Toast.makeText(getContext(), "已筛选: " + options[which], Toast.LENGTH_SHORT).show();
            })
            .show();
    }
    
    private void showDurationFilterDialog() {
        String[] options = SmartFilterHelper.getDurationFilterLabels();
        new AlertDialog.Builder(getContext())
            .setTitle("按视频时长筛选")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    applyFilterAndSort();
                } else {
                    // 显示加载提示
                    Toast.makeText(getContext(), "正在分析视频时长...", Toast.LENGTH_LONG).show();
                    
                    SmartFilterHelper.FilterType[] types = {
                        null,
                        SmartFilterHelper.FilterType.LONG_VIDEO,
                        SmartFilterHelper.FilterType.MEDIUM_VIDEO,
                        SmartFilterHelper.FilterType.SHORT_VIDEO
                    };
                    
                    // 在后台线程执行（比较耗时）
                    final int selectedIndex = which;
                    new Thread(() -> {
                        List<FileItem> filtered = SmartFilterHelper.filterByDuration(
                            allFiles, types[selectedIndex]);
                        
                        mainHandler.post(() -> {
                            displayedFiles = new ArrayList<>(filtered);
                            adapter.updateFiles(filtered);
                            updateEmptyView();
                            Toast.makeText(getContext(), 
                                "已筛选: " + options[selectedIndex] + " (" + filtered.size() + "个)", 
                                Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                }
            })
            .show();
    }
    
    private void applySmartFilter(SmartFilterHelper.FilterType filterType) {
        List<FileItem> filtered = SmartFilterHelper.smartFilter(allFiles, filterType);
        displayedFiles = new ArrayList<>(filtered);
        
        // 应用排序
        Collections.sort(displayedFiles, (o1, o2) -> {
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
        
        adapter.updateFiles(displayedFiles);
        updateEmptyView();
    }
    
    private void clearFilter() {
        applyFilterAndSort();
        Toast.makeText(getContext(), "已清除筛选", Toast.LENGTH_SHORT).show();
    }
    
    private void updateEmptyView() {
        if (displayedFiles.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateViewToggleButton() {
        if (isGridView) {
            // 当前是网格视图，点击后切换到列表视图
            viewToggleButton.setText("列表");
        } else {
            // 当前是列表视图，点击后切换到网格视图
            viewToggleButton.setText("网格");
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
        // 显示加载提示
        if (loadingView != null) {
            loadingView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
        }
        
        new Thread(() -> {
            List<File> targetDirectories = new ArrayList<>();
            
            // 根据选择的存储类型筛选目录
            if (isInternalStorage) {
                // 内部存储
                File dataDir = Environment.getDataDirectory();
                for (File dir : storageDirectories) {
                    if (dir.equals(dataDir)) {
                        targetDirectories.add(dir);
                        break;
                    }
                }
            } else {
                // 外部存储（所有非内部存储的目录）
                File dataDir = Environment.getDataDirectory();
                for (File dir : storageDirectories) {
                    if (!dir.equals(dataDir)) {
                        targetDirectories.add(dir);
                    }
                }
            }
            
            // 如果没有找到目标目录，使用所有目录
            if (targetDirectories.isEmpty()) {
                targetDirectories = storageDirectories;
            }
            
            // 先扫描所有视频文件，用于播放列表
            List<FileItem> allVideos = FileUtils.scanAllFiles(targetDirectories, FileItem.FileType.VIDEO);
            // 对视频列表按时间排序（用于播放顺序）
            Collections.sort(allVideos, (o1, o2) -> Long.compare(o2.getLastModified(), o1.getLastModified()));
            
            // 再根据当前筛选条件扫描文件，用于显示
            List<FileItem> files = FileUtils.scanAllFiles(targetDirectories, currentFilter);
            
            new Handler(Looper.getMainLooper()).post(() -> {
                allVideoFiles = allVideos; // 保存所有视频文件（已排序）
                allFiles = files; // 保存当前筛选的文件
                applyFilterAndSort();
                
                // 隐藏加载提示
                if (loadingView != null) {
                    loadingView.setVisibility(View.GONE);
                }
                
                // 停止刷新动画
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }).start();
    }

    private void applyFilterAndSort() {
        List<FileItem> filtered = new ArrayList<>();
        for (FileItem item : allFiles) {
            if (currentFilter == null || item.getFileType() == currentFilter) {
                // 应用搜索过滤
                if (TextUtils.isEmpty(currentSearchQuery) || 
                    item.getName().toLowerCase().contains(currentSearchQuery.toLowerCase())) {
                    filtered.add(item);
                }
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

        // 保存当前显示的文件列表（用于播放器）
        displayedFiles = new ArrayList<>(filtered);
        
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
     * 获取当前显示的视频文件路径列表（排序后）
     * 用于播放器的播放列表
     */
    private ArrayList<String> getCurrentDisplayedVideoList() {
        ArrayList<String> fileList = new ArrayList<>();
        for (FileItem item : displayedFiles) {
            if (item.getFileType() == FileItem.FileType.VIDEO) {
                fileList.add(item.getFile().getAbsolutePath());
            }
        }
        return fileList;
    }
    
    /**
     * 在当前显示的列表中找到视频的索引
     */
    private int findVideoIndexInDisplayedList(String filePath) {
        ArrayList<String> videoList = getCurrentDisplayedVideoList();
        int index = videoList.indexOf(filePath);
        return index >= 0 ? index : 0;
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
    
    /**
     * 删除选中的文件
     */
    private void deleteSelectedFiles(List<FileItem> selectedItems) {
        if (selectedItems.isEmpty()) {
            return;
        }
        
        // 检查是否有删除权限
        if (!PermissionManager.hasManageStoragePermission()) {
            Log.w(TAG, "deleteSelectedFiles: 没有 MANAGE_EXTERNAL_STORAGE 权限");
            Toast.makeText(getContext(), 
                "需要\"管理所有文件\"权限才能删除文件", 
                Toast.LENGTH_LONG).show();
            PermissionManager.requestManageStoragePermission(getActivity());
            return;
        }
        
        // 退出多选模式
        adapter.setSelectionMode(false);
        updateSelectionUI();
        
        // 检查executor是否有效
        if (deleteExecutor == null || deleteExecutor.isShutdown() || deleteExecutor.isTerminated()) {
            Log.e("HomeFragment", "deleteSelectedFiles: 删除线程池无效或已关闭");
            mainHandler.post(() -> {
                Toast.makeText(getContext(), "删除服务不可用", Toast.LENGTH_SHORT).show();
            });
            return;
        }
        
        // 异步删除文件
        deleteExecutor.execute(() -> {
            int successCount = 0;
            int failCount = 0;
            
            for (FileItem item : selectedItems) {
                File file = item.getFile();
                if (!file.exists()) {
                    continue;
                }
                
                boolean deleted = false;
                
                // Android 10+ 使用 MediaStore API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    deleted = deleteFileUsingMediaStore(file);
                    if (!deleted) {
                        // 尝试直接删除
                        deleted = file.delete();
                        if (!deleted && !file.exists()) {
                            deleted = true; // 文件已不存在
                        }
                    }
                } else {
                    // Android 9 及以下
                    if (file.canWrite()) {
                        deleted = file.delete();
                    }
                }
                
                if (deleted) {
                    successCount++;
                    Log.d(TAG, "deleteSelectedFiles: 删除成功 - " + file.getName());
                } else {
                    failCount++;
                    Log.e(TAG, "deleteSelectedFiles: 删除失败 - " + file.getName());
                }
            }
            
            // 在主线程更新UI和显示结果
            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;
            mainHandler.post(() -> {
                // 重新加载文件列表
                loadFiles();
                
                // 显示删除结果
                if (finalFailCount == 0) {
                    Toast.makeText(getContext(), 
                        getString(R.string.delete_success) + " (" + finalSuccessCount + ")", 
                        Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), 
                        getString(R.string.delete_success) + ": " + finalSuccessCount + 
                        ", " + getString(R.string.delete_failed) + ": " + finalFailCount, 
                        Toast.LENGTH_LONG).show();
                }
            });
        });
    }
    
    /**
     * 使用 MediaStore API 删除文件（Android 10+）
     */
    private boolean deleteFileUsingMediaStore(File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        
        try {
            ContentResolver resolver = getContext().getContentResolver();
            Uri uri = getMediaStoreUri(file);
            
            if (uri != null) {
                Log.d(TAG, "deleteFileUsingMediaStore: 找到 MediaStore URI - " + uri);
                
                try {
                    int deleted = resolver.delete(uri, null, null);
                    Log.d(TAG, "deleteFileUsingMediaStore: 删除结果 - " + deleted);
                    
                    if (deleted > 0) {
                        // 等待一下让系统完成删除操作
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            // 忽略
                        }
                        
                        // 检查文件是否真的被删除了
                        if (!file.exists()) {
                            return true;
                        }
                        
                        Log.w(TAG, "deleteFileUsingMediaStore: MediaStore 删除后文件仍存在");
                    }
                } catch (SecurityException securityException) {
                    // Android 10+ 删除其他应用的文件会抛出 RecoverableSecurityException
                    Log.e(TAG, "deleteFileUsingMediaStore: 需要用户授权删除", securityException);
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // 在主线程请求用户授权
                        mainHandler.post(() -> {
                            try {
                                if (securityException instanceof android.app.RecoverableSecurityException) {
                                    android.app.RecoverableSecurityException recoverableException = 
                                        (android.app.RecoverableSecurityException) securityException;
                                    
                                    // 启动系统的删除确认对话框
                                    android.app.PendingIntent pendingIntent = 
                                        recoverableException.getUserAction().getActionIntent();
                                    startIntentSenderForResult(
                                        pendingIntent.getIntentSender(),
                                        1001,  // requestCode
                                        null,
                                        0,
                                        0,
                                        0,
                                        null
                                    );
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "deleteFileUsingMediaStore: 无法启动授权对话框", e);
                                Toast.makeText(getContext(), "删除失败：需要用户授权", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                    return false;
                }
                
                // 如果 MediaStore 删除返回0或失败，尝试直接删除
                boolean directDeleted = file.delete();
                Log.d(TAG, "deleteFileUsingMediaStore: 直接删除结果 - " + directDeleted);
                return directDeleted;
            } else {
                Log.w(TAG, "deleteFileUsingMediaStore: 无法获取 MediaStore URI，尝试直接删除");
                boolean directDeleted = file.delete();
                Log.d(TAG, "deleteFileUsingMediaStore: 直接删除结果 - " + directDeleted);
                return directDeleted;
            }
        } catch (Exception e) {
            Log.e(TAG, "deleteFileUsingMediaStore: 删除异常", e);
            boolean directDeleted = file.delete();
            Log.d(TAG, "deleteFileUsingMediaStore: 异常后直接删除结果 - " + directDeleted);
            return directDeleted;
        }
    }
    
    /**
     * 获取文件的 MediaStore URI
     */
    private Uri getMediaStoreUri(File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        
        try {
            ContentResolver resolver = getContext().getContentResolver();
            String filePath = file.getAbsolutePath();
            
            // 尝试查找视频文件
            String[] projection = {MediaStore.Video.Media._ID};
            String selection = MediaStore.Video.Media.DATA + "=?";
            String[] selectionArgs = {filePath};
            
            Cursor cursor = resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                long id = cursor.getLong(idColumn);
                cursor.close();
                return Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, 
                    String.valueOf(id));
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            // 如果没找到，尝试音频文件
            cursor = resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                long id = cursor.getLong(idColumn);
                cursor.close();
                return Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 
                    String.valueOf(id));
            }
            
            if (cursor != null) {
                cursor.close();
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "getMediaStoreUri: 获取 URI 异常", e);
            return null;
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (deleteExecutor != null) {
            deleteExecutor.shutdown();
        }
        // 移除监听器，避免内存泄漏
        if (pendingDeleteManager != null) {
            pendingDeleteManager.setOnFileDeletedListener(null);
        }
    }
}


