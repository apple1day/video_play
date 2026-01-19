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
    private TextView loadingMoreView;
    private Spinner storageSpinner;
    private Button sortTimeButton;
    private Button sortSizeButton;
    private Button filterWatchedButton;
    private Button filterUnwatchedButton;
    private Button viewToggleButton;
    private Button smartFilterButton;
    private View toolbarLayout;
    private View selectionToolbarLayout;
    private Button selectAllButton;
    private Button selectFavoritesButton;
    private Button selectWatchedButton;
    private Button selectPendingDeletesButton;
    private Button deleteSelectedButton;
    private Button cancelSelectionButton;
    private TextView selectedCountText;
    private FileAdapter adapter;
    private List<FileItem> allFiles = new ArrayList<>();
    private List<FileItem> allVideoFiles = new ArrayList<>(); // 存储所有视频文件
    private List<FileItem> displayedFiles = new ArrayList<>(); // 当前显示的视频列表（排序后）
    private List<FileItem> filteredFiles = new ArrayList<>(); // 过滤排序后的完整列表
    private String currentSearchQuery = ""; // 当前搜索关键词
    private FileItem.FileType currentFilter = FileItem.FileType.VIDEO;
    private SortType currentSort = SortType.TIME;
    private boolean timeSortDescending = true; // 默认从新到旧（降序）
    private boolean sizeSortDescending = true; // 默认从大到小（降序）
    private boolean isGridView = false; // 默认列表视图
    private ExecutorService deleteExecutor;
    private Handler mainHandler;
    private PendingDeleteManager pendingDeleteManager;
    private FileListCacheManager cacheManager;
    
    // 分页相关
    private static final int PAGE_SIZE = 100; // 每页加载100个
    private int currentPage = 0; // 当前页码
    private boolean isLoading = false; // 是否正在加载
    private boolean hasMoreData = true; // 是否还有更多数据
    
    // 视频列表监听器（用于播放器预加载）
    private static VideoListLoadListener videoListLoadListener;
    
    /**
     * 视频列表加载监听器接口
     */
    public interface VideoListLoadListener {
        void onRequestLoadMore();
        ArrayList<String> getCurrentVideoList();
    }
    
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
        loadingMoreView = view.findViewById(R.id.loading_more_view);
        storageSpinner = view.findViewById(R.id.storage_spinner);
        sortTimeButton = view.findViewById(R.id.sort_time_button);
        sortSizeButton = view.findViewById(R.id.sort_size_button);
        filterWatchedButton = view.findViewById(R.id.filter_watched_button);
        filterUnwatchedButton = view.findViewById(R.id.filter_unwatched_button);
        viewToggleButton = view.findViewById(R.id.view_toggle_button);
        smartFilterButton = view.findViewById(R.id.smart_filter_button);
        toolbarLayout = view.findViewById(R.id.toolbar_layout);
        selectionToolbarLayout = view.findViewById(R.id.selection_toolbar_layout);
        selectAllButton = view.findViewById(R.id.select_all_button);
        selectFavoritesButton = view.findViewById(R.id.select_favorites_button);
        selectWatchedButton = view.findViewById(R.id.select_watched_button);
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
        
        // 设置上拉加载监听
        setupRecyclerViewScrollListener();
        
        // 设置搜索框
        setupSearchView();
        // 设置下拉刷新
        setupSwipeRefresh();
        setupStorageSpinner();
        setupSortButtons();
        setupWatchedFilterButtons();
        setupViewToggleButton();
        setupSmartFilterButton();
        setupSelectionButtons();
        
        // 初始化删除线程池和主线程Handler
        deleteExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化缓存管理器
        cacheManager = FileListCacheManager.getInstance(getContext());
        
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
        
        // 注册视频列表监听器，供 PlayerActivity 使用
        videoListLoadListener = new VideoListLoadListener() {
            @Override
            public void onRequestLoadMore() {
                // PlayerActivity 请求加载更多
                Log.d(TAG, "PlayerActivity 请求加载更多数据");
                if (hasMoreData && !isLoading) {
                    loadMoreData();
                }
            }
            
            @Override
            public ArrayList<String> getCurrentVideoList() {
                // 返回当前已加载的视频列表
                return getCurrentDisplayedVideoList();
            }
        };

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
    
    /**
     * 设置RecyclerView滚动监听，实现上拉加载更多
     */
    private void setupRecyclerViewScrollListener() {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // 只有向下滚动才触发
                if (dy <= 0) {
                    return;
                }
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                
                // 当滚动到底部附近（最后5个item）时加载更多
                if (!isLoading && hasMoreData && 
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                    loadMoreData();
                }
            }
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
            // 下拉刷新时重置分页并重新加载
            currentPage = 0;
            hasMoreData = true;
            cacheManager.clearCache();
            Toast.makeText(getContext(), "正在刷新视频列表...", Toast.LENGTH_SHORT).show();
            loadFiles(true); // 强制重新扫描
        });
    }
    
    private void setupStorageSpinner() {
        // 检查存储设备
        new Thread(() -> {
            storageDirectories = FileUtils.getStorageDirectories(getContext());
            
            // 判断是否有外部存储（非主存储的设备）
            File primaryStorage = Environment.getExternalStorageDirectory();
            hasExternalStorage = false;
            for (File dir : storageDirectories) {
                if (!dir.equals(primaryStorage) && dir.exists()) {
                    hasExternalStorage = true;
                    Log.d(TAG, "发现外部存储: " + dir.getAbsolutePath());
                    break;
                }
            }
            
            Log.d(TAG, "存储设备数量: " + storageDirectories.size() + ", 有外部存储: " + hasExternalStorage);
            
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
                        boolean newStorageType = (position == 0);
                        // 只有当存储类型真正改变时才重新加载
                        if (newStorageType != isInternalStorage) {
                            isInternalStorage = newStorageType;
                            Log.d(TAG, "切换存储类型: " + (isInternalStorage ? "内部存储" : "外部存储"));
                            loadFiles(false); // 使用缓存
                        }
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
        
        selectWatchedButton.setOnClickListener(v -> {
            adapter.selectAllWatched();
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
    
    private void setupWatchedFilterButtons() {
        // 设置"已看"按钮
        filterWatchedButton.setOnClickListener(v -> {
            filterByWatchedStatus(true);
            Toast.makeText(getContext(), "已筛选: 已看过的视频", Toast.LENGTH_SHORT).show();
        });
        
        // 设置"未看"按钮
        filterUnwatchedButton.setOnClickListener(v -> {
            filterByWatchedStatus(false);
            Toast.makeText(getContext(), "已筛选: 未看过的视频", Toast.LENGTH_SHORT).show();
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
            "按观看状态筛选",
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
                        showWatchedStatusFilterDialog();
                        break;
                    case 4:
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
                            // 保存过滤排序后的完整列表
                            filteredFiles = new ArrayList<>(filtered);
                            
                            // 重置分页状态，加载第一页
                            currentPage = 0;
                            hasMoreData = true;
                            displayedFiles.clear();
                            
                            // 加载第一页数据
                            loadPageData();
                            
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
        
        // 应用排序
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
        
        // 保存过滤排序后的完整列表
        filteredFiles = new ArrayList<>(filtered);
        
        // 重置分页状态，加载第一页
        currentPage = 0;
        hasMoreData = true;
        displayedFiles.clear();
        
        // 加载第一页数据
        loadPageData();
        
        updateEmptyView();
    }
    
    private void showWatchedStatusFilterDialog() {
        String[] options = new String[]{
            "全部视频",
            "未看过的视频",
            "已看过的视频"
        };
        
        new AlertDialog.Builder(getContext())
            .setTitle("按观看状态筛选")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    applyFilterAndSort();
                } else if (which == 1) {
                    // 筛选未看过的视频
                    filterByWatchedStatus(false);
                } else if (which == 2) {
                    // 筛选已看过的视频
                    filterByWatchedStatus(true);
                }
                Toast.makeText(getContext(), "已筛选: " + options[which], Toast.LENGTH_SHORT).show();
            })
            .show();
    }
    
    /**
     * 根据观看状态筛选视频
     * @param watchedOnly true=只显示已看过的，false=只显示未看过的
     */
    private void filterByWatchedStatus(boolean watchedOnly) {
        WatchedManager watchedManager = WatchedManager.getInstance(getContext());
        
        List<FileItem> filtered = new ArrayList<>();
        for (FileItem item : allFiles) {
            if (item.getFileType() == FileItem.FileType.VIDEO) {
                String filePath = item.getFile().getAbsolutePath();
                boolean isWatched = watchedManager.isWatched(filePath);
                
                // 应用搜索过滤
                boolean matchesSearch = TextUtils.isEmpty(currentSearchQuery) || 
                    item.getName().toLowerCase().contains(currentSearchQuery.toLowerCase());
                
                // 根据筛选条件决定是否添加
                if (matchesSearch && ((watchedOnly && isWatched) || (!watchedOnly && !isWatched))) {
                    filtered.add(item);
                }
            }
        }
        
        // 应用排序
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
        
        // 保存过滤排序后的完整列表
        filteredFiles = new ArrayList<>(filtered);
        
        // 重置分页状态，加载第一页
        currentPage = 0;
        hasMoreData = true;
        displayedFiles.clear();
        
        // 加载第一页数据
        loadPageData();
        
        updateEmptyView();
    }
    
    private void clearFilter() {
        applyFilterAndSort();
        Toast.makeText(getContext(), "已清除筛选", Toast.LENGTH_SHORT).show();
    }
    
    private void updateEmptyView() {
        if (filteredFiles.isEmpty()) {
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
            loadFiles(false); // 使用缓存
        }
    }

    private void loadFiles() {
        loadFiles(false); // 默认使用缓存
    }

    private void loadFiles(boolean forceRefresh) {
        // 重置分页状态
        currentPage = 0;
        hasMoreData = true;
        displayedFiles.clear();
        
        // 如果不是强制刷新，先尝试从缓存加载
        if (!forceRefresh) {
            List<FileItem> cachedFiles = cacheManager.loadFileList(isInternalStorage);
            if (cachedFiles != null && !cachedFiles.isEmpty()) {
                Log.d(TAG, "使用缓存数据: " + cachedFiles.size() + " 个文件");
                
                // 在后台线程处理数据
                new Thread(() -> {
                    List<FileItem> videos = new ArrayList<>();
                    for (FileItem item : cachedFiles) {
                        if (item.getFileType() == FileItem.FileType.VIDEO) {
                            videos.add(item);
                        }
                    }
                    // 对视频列表按时间排序
                    Collections.sort(videos, (o1, o2) -> Long.compare(o2.getLastModified(), o1.getLastModified()));
                    
                    // 回到主线程更新UI
                    new Handler(Looper.getMainLooper()).post(() -> {
                        allVideoFiles = videos;
                        allFiles = cachedFiles;
                        applyFilterAndSort(); // 这会触发第一页加载
                        
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
                
                return;
            }
        }
        
        // 缓存无效或强制刷新，显示加载提示
        if (loadingView != null) {
            loadingView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE); // 立即显示RecyclerView，准备增量显示
        }
        
        new Thread(() -> {
            List<File> targetDirectories = new ArrayList<>();
            
            // 根据选择的存储类型筛选目录
            // 修复：使用 Environment.getExternalStorageDirectory() 作为主存储判断依据
            File primaryStorage = Environment.getExternalStorageDirectory();
            
            if (isInternalStorage) {
                // 内部存储（手机本机存储）
                if (primaryStorage != null && primaryStorage.exists()) {
                    targetDirectories.add(primaryStorage);
                }
            } else {
                // 外部存储（所有非主存储的目录，如U盘、SD卡）
                for (File dir : storageDirectories) {
                    if (!dir.equals(primaryStorage) && dir.exists()) {
                        targetDirectories.add(dir);
                    }
                }
            }
            
            // 如果没有找到目标目录，使用所有目录
            if (targetDirectories.isEmpty()) {
                targetDirectories = storageDirectories;
            }
            
            Log.d(TAG, "扫描目录: " + targetDirectories.size() + " 个, 类型: " + (isInternalStorage ? "内部存储" : "外部存储"));
            
            // 使用异步增量加载，边扫描边显示
            final List<FileItem> allScannedFiles = new ArrayList<>();
            final List<FileItem> allScannedVideos = new ArrayList<>();
            
            FileUtils.scanAllFilesAsync(targetDirectories, currentFilter, new FileUtils.ScanCallback() {
                @Override
                public void onBatchLoaded(List<FileItem> batch) {
                    // 每批文件加载完成，立即在主线程更新UI
                    new Handler(Looper.getMainLooper()).post(() -> {
                        synchronized (allScannedFiles) {
                            allScannedFiles.addAll(batch);
                            
                            // 提取视频文件
                            for (FileItem item : batch) {
                                if (item.getFileType() == FileItem.FileType.VIDEO) {
                                    allScannedVideos.add(item);
                                }
                            }
                            
                            // 更新UI显示
                            allFiles = new ArrayList<>(allScannedFiles);
                            allVideoFiles = new ArrayList<>(allScannedVideos);
                            applyFilterAndSort();
                            
                            Log.d(TAG, "已加载 " + allScannedFiles.size() + " 个文件（增量更新）");
                        }
                    });
                }
                
                @Override
                public void onScanComplete(List<FileItem> allFiles) {
                    // 所有文件扫描完成
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // 对视频列表按时间排序（用于播放顺序）
                        Collections.sort(allScannedVideos, (o1, o2) -> 
                            Long.compare(o2.getLastModified(), o1.getLastModified()));
                        
                        allVideoFiles = new ArrayList<>(allScannedVideos);
                        
                        // 保存到缓存
                        cacheManager.saveFileList(allScannedFiles, isInternalStorage);
                        
                        // 最后一次排序和过滤
                        applyFilterAndSort();
                        
                        // 隐藏加载提示
                        if (loadingView != null) {
                            loadingView.setVisibility(View.GONE);
                        }
                        
                        // 停止刷新动画
                        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        
                        String storageType = isInternalStorage ? "内部存储" : "外部存储";
                        Toast.makeText(getContext(), storageType + ": 已加载 " + allScannedFiles.size() + " 个文件", 
                            Toast.LENGTH_SHORT).show();
                        
                        Log.d(TAG, "扫描完成: 总共 " + allScannedFiles.size() + " 个文件，" + 
                            allScannedVideos.size() + " 个视频");
                    });
                }
            });
        }).start();
    }

    private void applyFilterAndSort() {
        // 过滤
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

        // 排序
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

        // 保存过滤排序后的完整列表
        filteredFiles = new ArrayList<>(filtered);
        
        // 重置分页状态，加载第一页
        currentPage = 0;
        hasMoreData = true;
        displayedFiles.clear();
        
        // 加载第一页数据
        loadPageData();
        
        if (filteredFiles.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * 加载一页数据（首次加载）
     */
    private void loadPageData() {
        int startIndex = currentPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, filteredFiles.size());
        
        if (startIndex >= filteredFiles.size()) {
            hasMoreData = false;
            return;
        }
        
        List<FileItem> pageData = filteredFiles.subList(startIndex, endIndex);
        displayedFiles.addAll(pageData);
        
        adapter.updateFiles(new ArrayList<>(displayedFiles));
        
        currentPage++;
        hasMoreData = endIndex < filteredFiles.size();
        
        Log.d(TAG, "加载第 " + currentPage + " 页，显示 " + displayedFiles.size() + 
            "/" + filteredFiles.size() + " 个文件");
    }
    
    /**
     * 加载更多数据（上拉加载）
     */
    private void loadMoreData() {
        if (isLoading || !hasMoreData) {
            return;
        }
        
        isLoading = true;
        
        // 显示加载提示
        if (loadingMoreView != null) {
            loadingMoreView.setVisibility(View.VISIBLE);
        }
        
        Log.d(TAG, "开始加载更多数据...");
        
        // 在后台线程处理以避免卡顿
        new Thread(() -> {
            int startIndex = currentPage * PAGE_SIZE;
            int endIndex = Math.min(startIndex + PAGE_SIZE, filteredFiles.size());
            
            if (startIndex >= filteredFiles.size()) {
                mainHandler.post(() -> {
                    hasMoreData = false;
                    isLoading = false;
                    if (loadingMoreView != null) {
                        loadingMoreView.setVisibility(View.GONE);
                    }
                });
                return;
            }
            
            List<FileItem> pageData = new ArrayList<>(filteredFiles.subList(startIndex, endIndex));
            
            mainHandler.post(() -> {
                displayedFiles.addAll(pageData);
                adapter.appendFiles(pageData);
                
                currentPage++;
                hasMoreData = endIndex < filteredFiles.size();
                isLoading = false;
                
                // 隐藏加载提示
                if (loadingMoreView != null) {
                    loadingMoreView.setVisibility(View.GONE);
                }
                
                Log.d(TAG, "加载更多完成，当前显示 " + displayedFiles.size() + 
                    "/" + filteredFiles.size() + " 个文件");
                
                if (!hasMoreData) {
                    Toast.makeText(getContext(), "已加载全部视频", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
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
                
                // 如果删除成功，检查文件是否真的被删除了
                if (deleted) {
                    // 等待一小段时间，让系统完成删除操作
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // 忽略
                    }
                    
                    // 检查文件是否真的被删除了
                    if (file.exists()) {
                        // 文件仍然存在，可能是0字节占位文件
                        Log.w(TAG, "deleteSelectedFiles: 文件删除后仍存在，尝试再次删除 - " + file.getName());
                        
                        // 再次尝试删除
                        deleted = file.delete();
                        
                        // 再次检查
                        if (file.exists()) {
                            // 如果是0字节文件，也认为删除成功
                            if (file.length() == 0) {
                                Log.d(TAG, "deleteSelectedFiles: 删除成功（残留0字节文件已清理） - " + file.getName());
                                deleted = true;
                            } else {
                                Log.e(TAG, "deleteSelectedFiles: 删除失败（文件仍然存在，大小: " + file.length() + " bytes） - " + file.getName());
                                deleted = false;
                            }
                        }
                    }
                    
                    // 通知媒体扫描器更新
                    if (deleted) {
                        try {
                            android.media.MediaScannerConnection.scanFile(
                                getContext(),
                                new String[]{file.getAbsolutePath()},
                                null,
                                null
                            );
                        } catch (Exception e) {
                            Log.w(TAG, "deleteSelectedFiles: 通知媒体扫描器失败", e);
                        }
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
                
                // 检查文件是否真的被删除了
                if (directDeleted && file.exists()) {
                    // 再次尝试删除
                    directDeleted = file.delete();
                    // 如果仍然存在但是0字节，也认为成功
                    if (file.exists() && file.length() == 0) {
                        directDeleted = true;
                    }
                }
                
                return directDeleted;
            } else {
                Log.w(TAG, "deleteFileUsingMediaStore: 无法获取 MediaStore URI，尝试直接删除");
                boolean directDeleted = file.delete();
                Log.d(TAG, "deleteFileUsingMediaStore: 直接删除结果 - " + directDeleted);
                
                // 检查文件是否真的被删除了
                if (directDeleted && file.exists()) {
                    // 再次尝试删除
                    directDeleted = file.delete();
                    // 如果仍然存在但是0字节，也认为成功
                    if (file.exists() && file.length() == 0) {
                        directDeleted = true;
                    }
                }
                
                return directDeleted;
            }
        } catch (Exception e) {
            Log.e(TAG, "deleteFileUsingMediaStore: 删除异常", e);
            boolean directDeleted = file.delete();
            Log.d(TAG, "deleteFileUsingMediaStore: 异常后直接删除结果 - " + directDeleted);
            
            // 检查文件是否真的被删除了
            if (directDeleted && file.exists()) {
                // 再次尝试删除
                directDeleted = file.delete();
                // 如果仍然存在但是0字节，也认为成功
                if (file.exists() && file.length() == 0) {
                    directDeleted = true;
                }
            }
            
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
        // 清除视频列表监听器
        videoListLoadListener = null;
    }
    
    /**
     * 获取视频列表监听器（供 PlayerActivity 使用）
     */
    public static VideoListLoadListener getVideoListLoadListener() {
        return videoListLoadListener;
    }
}


