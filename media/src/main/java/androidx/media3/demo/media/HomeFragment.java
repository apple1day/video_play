package androidx.media3.demo.media;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
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
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.WindowManager;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    
    private SearchView searchView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private View emptyView;
    private View loadingView;
    private TextView loadingTextView;
    private TextView loadingMoreView;
    private Spinner storageSpinner;
    private Button sortTimeButton;
    private Button sortSizeButton;
    private Button filterWatchedButton;
    private Button filterUnwatchedButton;
    private View viewToggleButton;
    private Button smartFilterButton;
    private Button deleteAllPendingButton;
    private View toolbarLayout;
    private View selectionToolbarLayout;
    // 新增：顶部图标按钮
    private View btnSearch;
    private View btnSort;
    private View btnFilter;
    private android.app.Dialog searchDialog;
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
    private PendingDeleteManager.OnFileDeletedListener pendingDeleteListener;
    private FileListCacheManager cacheManager;
    private androidx.media3.demo.media.viewmodel.VideoListViewModel videoListViewModel;
    
    // 分页相关
    private static final int FIRST_PAGE_SIZE = 10;  // 首次仅加载10个，快速展示
    private static final int PAGE_SIZE = 50;         // 后续每页加载50个
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
    private boolean isInternalStorage = true; // 保留字段以兼容旧缓存接口；主页扫描已改为"全部存储"
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
        loadingTextView = view.findViewById(R.id.loading_text);
        loadingMoreView = view.findViewById(R.id.loading_more_view);
        storageSpinner = view.findViewById(R.id.storage_spinner);
        sortTimeButton = view.findViewById(R.id.sort_time_button);
        sortSizeButton = view.findViewById(R.id.sort_size_button);
        filterWatchedButton = view.findViewById(R.id.filter_watched_button);
        filterUnwatchedButton = view.findViewById(R.id.filter_unwatched_button);
        viewToggleButton = view.findViewById(R.id.view_toggle_button);
        smartFilterButton = view.findViewById(R.id.smart_filter_button);
        deleteAllPendingButton = view.findViewById(R.id.delete_all_pending_button);
        toolbarLayout = view.findViewById(R.id.toolbar_layout);
        selectionToolbarLayout = view.findViewById(R.id.selection_toolbar_layout);
        btnSearch = view.findViewById(R.id.btn_search);
        btnSort = view.findViewById(R.id.btn_sort);
        btnFilter = view.findViewById(R.id.btn_filter);
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

        // 长按快捷菜单
        adapter.setOnItemLongClickListener(fileItem -> {
            if (!adapter.isSelectionMode()) {
                showVideoQuickMenu(fileItem);
                return true;
            }
            return false;
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
        setupDeleteAllPendingButton();
        setupSelectionButtons();
        setupIconButtons();
        
        // 初始化删除线程池和主线程Handler
        deleteExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化缓存管理器
        cacheManager = FileListCacheManager.getInstance(getContext());
        
        // 初始化待删除管理器并设置监听器
        pendingDeleteManager = PendingDeleteManager.getInstance(getContext());
        pendingDeleteListener = new PendingDeleteManager.OnFileDeletedListener() {
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
        };
        pendingDeleteManager.addOnFileDeletedListener(pendingDeleteListener);
        
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
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText;
                applyFilterAndSort();
                return true;
            }
        });
        
        searchView.setOnCloseListener(() -> {
            currentSearchQuery = "";
            applyFilterAndSort();
            return false;
        });
    }
    
    /**
     * 设置顶部图标按钮：搜索、排序、筛选
     */
    private void setupIconButtons() {
        // 搜索按钮：弹出搜索对话框
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> showSearchDialog());
        }
        // 排序按钮：弹出底部排序菜单
        if (btnSort != null) {
            btnSort.setOnClickListener(v -> showSortBottomSheet());
        }
        // 筛选/设置按钮：弹出底部筛选菜单
        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> showFilterBottomSheet());
        }
    }

    /** 弹出搜索对话框 */
    private void showSearchDialog() {
        if (!isAdded() || getContext() == null) return;
        android.widget.EditText editText = new android.widget.EditText(getContext());
        editText.setHint("搜索视频...");
        editText.setText(currentSearchQuery);
        editText.setSingleLine(true);
        editText.setPadding(48, 24, 48, 24);
        editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                applyFilterAndSort();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        new AlertDialog.Builder(getContext())
            .setTitle("搜索视频")
            .setView(editText)
            .setPositiveButton("搜索", null)
            .setNegativeButton("清除", (d, w) -> {
                currentSearchQuery = "";
                applyFilterAndSort();
            })
            .show();
        editText.requestFocus();
        editText.post(() -> {
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    /** 弹出底部排序菜单（仿微信风格） */
    private void showSortBottomSheet() {
        if (!isAdded() || getContext() == null) return;
        android.app.Dialog dialog = new android.app.Dialog(getContext(), R.style.BottomSheetDialogStyle);
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bottom_sheet_bg);
        int dp16 = (int)(16 * getResources().getDisplayMetrics().density);
        int dp8  = (int)(8  * getResources().getDisplayMetrics().density);
        container.setPadding(0, dp8, 0, dp16);

        // 标题
        TextView title = new TextView(getContext());
        title.setText("排序方式");
        title.setTextSize(15);
        title.setTextColor(0xFF999999);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp16, 0, dp8);
        container.addView(title);

        // 分隔线
        View divider = new View(getContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFEEEEEE);
        container.addView(divider);

        String[] labels = {"按时间：最新优先", "按时间：最早优先", "按大小：从大到小", "按大小：从小到大"};
        boolean[] states = {
            currentSort == SortType.TIME && timeSortDescending,
            currentSort == SortType.TIME && !timeSortDescending,
            currentSort == SortType.SIZE && sizeSortDescending,
            currentSort == SortType.SIZE && !sizeSortDescending
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView item = new TextView(getContext());
            item.setText(states[i] ? "✓  " + labels[i] : "    " + labels[i]);
            item.setTextSize(16);
            item.setTextColor(states[i] ? 0xFFFF6B35 : 0xFF333333);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp16, 0, dp16);
            item.setClickable(true);
            item.setFocusable(true);
            item.setBackgroundResource(android.R.drawable.list_selector_background);
            item.setOnClickListener(v -> {
                switch (idx) {
                    case 0: currentSort = SortType.TIME; timeSortDescending = true; break;
                    case 1: currentSort = SortType.TIME; timeSortDescending = false; break;
                    case 2: currentSort = SortType.SIZE; sizeSortDescending = true; break;
                    case 3: currentSort = SortType.SIZE; sizeSortDescending = false; break;
                }
                applyFilterAndSort();
                dialog.dismiss();
            });
            container.addView(item);
            if (i < labels.length - 1) {
                View sep = new View(getContext());
                sep.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                sep.setBackgroundColor(0xFFEEEEEE);
                container.addView(sep);
            }
        }

        // 取消按钮
        View cancelDiv = new View(getContext());
        cancelDiv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp8));
        cancelDiv.setBackgroundColor(0xFFF5F5F5);
        container.addView(cancelDiv);
        TextView cancel = new TextView(getContext());
        cancel.setText("取消");
        cancel.setTextSize(16);
        cancel.setTextColor(0xFF333333);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp16, 0, dp16);
        cancel.setClickable(true);
        cancel.setFocusable(true);
        cancel.setBackgroundResource(android.R.drawable.list_selector_background);
        cancel.setOnClickListener(v -> dialog.dismiss());
        container.addView(cancel);

        dialog.setContentView(container);
        android.view.Window win = dialog.getWindow();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
    }

    /** 弹出底部筛选菜单（已看/未看/智能筛选/清除待删） */
    private void showFilterBottomSheet() {
        if (!isAdded() || getContext() == null) return;
        android.app.Dialog dialog = new android.app.Dialog(getContext(), R.style.BottomSheetDialogStyle);
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bottom_sheet_bg);
        int dp16 = (int)(16 * getResources().getDisplayMetrics().density);
        int dp8  = (int)(8  * getResources().getDisplayMetrics().density);
        container.setPadding(0, dp8, 0, dp16);

        // 标题
        TextView title = new TextView(getContext());
        title.setText("筛选与操作");
        title.setTextSize(15);
        title.setTextColor(0xFF999999);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp16, 0, dp8);
        container.addView(title);
        View divider0 = new View(getContext());
        divider0.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider0.setBackgroundColor(0xFFEEEEEE);
        container.addView(divider0);

        String[] labels   = {"筛选：已看过的视频", "筛选：未看过的视频", "智能筛选...", "清除筛选", "清除全部待删除"};
        int[]    colors   = {0xFF333333, 0xFF333333, 0xFF333333, 0xFF333333, 0xFFE53935};
        Runnable[] actions = {
            () -> { filterByWatchedStatus(true);  showToast("已筛选: 已看过的视频"); },
            () -> { filterByWatchedStatus(false); showToast("已筛选: 未看过的视频"); },
            () -> showSmartFilterDialog(),
            () -> clearFilter(),
            () -> {
                int count = pendingDeleteManager.getPendingCount();
                if (count == 0) { showToast("没有待删除的视频"); return; }
                new AlertDialog.Builder(getContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除所有 " + count + " 个待删除的视频吗？")
                    .setPositiveButton("删除", (d2, w) -> {
                        pendingDeleteManager.deleteAllPending();
                        showToast("正在删除 " + count + " 个视频...");
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final Runnable action = actions[i];
            TextView item = new TextView(getContext());
            item.setText(labels[i]);
            item.setTextSize(16);
            item.setTextColor(colors[i]);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp16, 0, dp16);
            item.setClickable(true);
            item.setFocusable(true);
            item.setBackgroundResource(android.R.drawable.list_selector_background);
            item.setOnClickListener(v -> { action.run(); dialog.dismiss(); });
            container.addView(item);
            if (i < labels.length - 1) {
                View sep = new View(getContext());
                sep.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                sep.setBackgroundColor(0xFFEEEEEE);
                container.addView(sep);
            }
        }

        // 取消
        View cancelDiv = new View(getContext());
        cancelDiv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp8));
        cancelDiv.setBackgroundColor(0xFFF5F5F5);
        container.addView(cancelDiv);
        TextView cancel = new TextView(getContext());
        cancel.setText("取消");
        cancel.setTextSize(16);
        cancel.setTextColor(0xFF333333);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp16, 0, dp16);
        cancel.setClickable(true);
        cancel.setFocusable(true);
        cancel.setBackgroundResource(android.R.drawable.list_selector_background);
        cancel.setOnClickListener(v -> dialog.dismiss());
        container.addView(cancel);

        dialog.setContentView(container);
        android.view.Window win = dialog.getWindow();
        if (win != null) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            win.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
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
                
                RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                if (lm == null) {
                    return;
                }
                
                int visibleItemCount = lm.getChildCount();
                int totalItemCount = lm.getItemCount();
                int firstVisibleItemPosition = 0;
                
                if (lm instanceof LinearLayoutManager) {
                    firstVisibleItemPosition = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
                } else if (lm instanceof GridLayoutManager) {
                    firstVisibleItemPosition = ((GridLayoutManager) lm).findFirstVisibleItemPosition();
                }
                
                // 当滚动到底部附近（最后5个item）时加载更多
                if (!isLoading && hasMoreData && 
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                    loadMoreData();
                }
            }
        });
    }
    
    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeColors(
            0xFF_FF6B35,  // 品牌橙红
            0xFF_E53935,  // 深红
            0xFF_FF8A65   // 浅橙
        );
        
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // 下拉刷新时重置分页并重新加载
            currentPage = 0;
            hasMoreData = true;
            cacheManager.clearCache();
            showToast("正在刷新视频列表...");
            loadFiles(true); // 强制重新扫描
        });
    }
    
    private void setupStorageSpinner() {
        // 主页存储切换已废弃：现在默认扫描所有存储设备（内部 + 外部）。
        // 真正的存储选择交给"文件夹"Tab 处理。
        if (storageSpinner != null) {
            storageSpinner.setVisibility(View.GONE);
        }
        // 用 ApplicationContext（生命周期长于 Fragment），避免后台线程持有 Fragment 导致泄漏
        if (!isAdded() || getContext() == null) return;
        final android.content.Context appCtx = getContext().getApplicationContext();
        new Thread(() -> {
            List<File> dirs = FileUtils.getStorageDirectories(appCtx);
            File primaryStorage = Environment.getExternalStorageDirectory();
            boolean hasExt = false;
            for (File dir : dirs) {
                if (!dir.equals(primaryStorage) && dir.exists()) {
                    hasExt = true;
                    Log.d(TAG, "发现外部存储: " + dir.getAbsolutePath());
                    break;
                }
            }
            final boolean finalHasExt = hasExt;
            final List<File> finalDirs = dirs;
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    storageDirectories = finalDirs;
                    hasExternalStorage = finalHasExt;
                    Log.d(TAG, "存储设备数量: " + storageDirectories.size() + ", 有外部存储: " + hasExternalStorage);
                });
            }
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
            showToast("已筛选: 已看过的视频");
        });
        
        // 设置"未看"按钮
        filterUnwatchedButton.setOnClickListener(v -> {
            filterByWatchedStatus(false);
            showToast("已筛选: 未看过的视频");
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
        if (viewToggleButton != null) {
            viewToggleButton.setOnClickListener(v -> {
                isGridView = !isGridView;
                updateViewToggleButton();
                switchLayoutManager();
            });
        }
    }
    
    private void setupSmartFilterButton() {
        if (smartFilterButton != null) {
            smartFilterButton.setOnClickListener(v -> showSmartFilterDialog());
        }
    }
    
    private void setupDeleteAllPendingButton() {
        if (deleteAllPendingButton != null) {
            deleteAllPendingButton.setOnClickListener(v -> {
                // 获取待删除文件数量
                int pendingCount = pendingDeleteManager.getPendingCount();
                
                if (pendingCount == 0) {
                    showToast("没有待删除的视频");
                    return;
                }
                
                // 确认对话框
                new AlertDialog.Builder(getContext())
                    .setTitle("确认删除")
                    .setMessage("确定要删除所有 " + pendingCount + " 个待删除的视频吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        // 执行批量删除
                        pendingDeleteManager.deleteAllPending();
                        showToast("正在删除 " + pendingCount + " 个视频...");
                    })
                    .setNegativeButton("取消", null)
                    .show();
            });
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
                showToast("已筛选: " + options[which]);
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
                showToast("已筛选: " + options[which]);
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
                    showToastLong("正在分析视频时长...");
                    
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
                            showToast("已筛选: " + options[selectedIndex] + " (" + filtered.size() + "个)");
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
                showToast("已筛选: " + options[which]);
            })
            .show();
    }
    
    /**
     * 根据观看状态筛选视频（优化版 - 在后台线程执行）
     * @param watchedOnly true=只显示已看过的，false=只显示未看过的
     */
    private void filterByWatchedStatus(boolean watchedOnly) {
        // 显示加载提示
        if (loadingView != null) {
            loadingView.setVisibility(View.VISIBLE);
            if (loadingTextView != null) loadingTextView.setText("正在筛选视频...");
        }
        
        // 在后台线程执行筛选操作，避免阻塞主线程
        new Thread(() -> {
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
            
            // 回到主线程更新UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 保存过滤排序后的完整列表
                    filteredFiles = new ArrayList<>(filtered);
                    
                    // 重置分页状态，加载第一页
                    currentPage = 0;
                    hasMoreData = true;
                    displayedFiles.clear();
                    
                    // 加载第一页数据
                    loadPageData();
                    
                    // 隐藏加载提示
                    if (loadingView != null) {
                        loadingView.setVisibility(View.GONE);
                    }
                    
                    updateEmptyView();
                });
            }
        }).start();
    }
    
    /** 长按视频项弹出快捷菜单（分享 / 收藏 / 待删除 / 信息）。 */
    private void showVideoQuickMenu(FileItem fileItem) {
        if (!isAdded() || getContext() == null) return;
        String path = fileItem.getFile().getAbsolutePath();
        FavoriteManager favMgr = FavoriteManager.getInstance(getContext());
        boolean isFav = favMgr.isFavorite(path);
        boolean isPending = pendingDeleteManager.isPendingDelete(path);

        String[] options = {
            isFav     ? "取消收藏 ★" : "收藏 ☆",
            isPending ? "取消待删除" : "标记待删除",
            "分享",
            "视频信息"
        };

        new AlertDialog.Builder(getContext())
            .setTitle(fileItem.getName())
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // 收藏
                        boolean nowFav = favMgr.toggleFavorite(path);
                        showToast(nowFav ? "已收藏" : "已取消收藏");
                        adapter.notifyDataSetChanged();
                        break;
                    case 1: // 待删除
                        boolean nowPending = pendingDeleteManager.togglePendingDelete(path);
                        showToast(nowPending ? "已标记待删除" : "已取消待删除");
                        adapter.notifyDataSetChanged();
                        break;
                    case 2: // 分享
                        try {
                            android.net.Uri uri = FileShareHelper.getShareUri(getContext(), fileItem.getFile());
                            Intent shareIntent = new Intent(Intent.ACTION_SEND)
                                .setType("video/*")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(shareIntent, "分享视频"));
                        } catch (Exception e) {
                            showToast("分享失败: " + e.getMessage());
                        }
                        break;
                    case 3: // 信息
                        long size = fileItem.getFile().length();
                        long mod  = fileItem.getFile().lastModified();
                        String sizeStr = android.text.format.Formatter.formatFileSize(getContext(), size);
                        String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(mod));
                        new AlertDialog.Builder(getContext())
                            .setTitle("视频信息")
                            .setMessage("文件名：" + fileItem.getName()
                                + "\n大小：" + sizeStr
                                + "\n修改时间：" + dateStr
                                + "\n路径：" + path)
                            .setPositiveButton("关闭", null)
                            .show();
                        break;
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 安全显示 Toast：使用 ApplicationContext，避免 Fragment detach 后 getContext() 返回 null 引发 NPE。
     * 替代所有 Toast.makeText(getContext(), ...) 的调用。
     */
    private void showToast(String message) {
        Context ctx = isAdded() ? getContext() : null;
        if (ctx != null) {
            Toast.makeText(ctx.getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showToastLong(String message) {
        Context ctx = isAdded() ? getContext() : null;
        if (ctx != null) {
            Toast.makeText(ctx.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void clearFilter() {
        applyFilterAndSort();
        showToast("已清除筛选");
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
        if (viewToggleButton instanceof TextView) {
            ((TextView) viewToggleButton).setText(isGridView ? "☰" : "⊞");
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
        loadFiles(false);
    }

    /**
     * 加载视频列表。已经搬到 {@link androidx.media3.demo.media.viewmodel.VideoListViewModel}：
     * Fragment 只需要喊一声"我要扫描"，剩下的扫描线程、增量回调、磁盘缓存命中、状态切换
     * 全由 ViewModel + VideoRepository 处理。Fragment 通过 LiveData 订阅结果，
     * 旋转屏 / 切 Tab 也不会重复扫盘。
     */
    private void loadFiles(boolean forceRefresh) {
        // 重置分页状态
        currentPage = 0;
        hasMoreData = true;
        displayedFiles.clear();

        if (videoListViewModel == null) {
            videoListViewModel = new androidx.lifecycle.ViewModelProvider(this)
                    .get(androidx.media3.demo.media.viewmodel.VideoListViewModel.class);
            observeVideoListViewModel();
        }
        videoListViewModel.loadVideos(currentFilter, forceRefresh);
    }

    /** ViewModel LiveData → UI 绑定。仅在第一次 loadFiles 时挂载。 */
    private void observeVideoListViewModel() {
        videoListViewModel.phase.observe(getViewLifecycleOwner(), phase -> {
            if (phase == null) return;
            switch (phase) {
                case SCANNING:
                    if (loadingView != null) {
                        loadingView.setVisibility(View.VISIBLE);
                        emptyView.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                    }
                    break;
                case COMPLETED:
                    if (loadingView != null) loadingView.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    break;
                default:
                    break;
            }
        });

        videoListViewModel.incrementalBatch.observe(getViewLifecycleOwner(), batch -> {
            if (batch == null || batch.isEmpty()) return;
            // 增量阶段只更新加载提示，不触发排序刷新
            if (loadingView != null && loadingTextView != null) {
                int found = videoListViewModel.allVideos.getValue() != null
                        ? videoListViewModel.allVideos.getValue().size() : 0;
                loadingTextView.setText("正在加载视频... 已找到 " + (found + batch.size()) + " 个");
            }
        });

        videoListViewModel.allFiles.observe(getViewLifecycleOwner(), files -> {
            if (files == null) return;
            allFiles = new ArrayList<>(files);
        });

        videoListViewModel.allVideos.observe(getViewLifecycleOwner(), videos -> {
            if (videos == null) return;
            allVideoFiles = new ArrayList<>(videos);
            applyFilterAndSort();
        });
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
     * 加载一页数据（首次加载）。首页用 FIRST_PAGE_SIZE，后续用 PAGE_SIZE。
     */
    private void loadPageData() {
        int pageSize = (currentPage == 0) ? FIRST_PAGE_SIZE : PAGE_SIZE;
        int startIndex = (currentPage == 0) ? 0 : FIRST_PAGE_SIZE + (currentPage - 1) * PAGE_SIZE;
        int endIndex = Math.min(startIndex + pageSize, filteredFiles.size());
        
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
        
        if (loadingMoreView != null) {
            loadingMoreView.setVisibility(View.VISIBLE);
        }
        
        Log.d(TAG, "开始加载更多数据...");
        
        new Thread(() -> {
            // 计算本次加载的起始/结束位置（与 loadPageData 对齐）
            int startIndex = (currentPage == 0) ? 0 : FIRST_PAGE_SIZE + (currentPage - 1) * PAGE_SIZE;
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
                    showToast("已加载全部视频");
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
     * 删除选中的文件
     */
    private void deleteSelectedFiles(List<FileItem> selectedItems) {
        if (selectedItems.isEmpty()) {
            return;
        }
        
        // 检查是否有删除权限
        if (!PermissionManager.hasManageStoragePermission()) {
            Log.w(TAG, "deleteSelectedFiles: 没有 MANAGE_EXTERNAL_STORAGE 权限");
            showToastLong("需要\"管理所有文件\"权限才能删除文件");
            PermissionManager.requestManageStoragePermission(getActivity());
            return;
        }
        
        // 退出多选模式
        adapter.setSelectionMode(false);
        updateSelectionUI();
        
        // 检查executor是否有效
        if (deleteExecutor == null || deleteExecutor.isShutdown() || deleteExecutor.isTerminated()) {
            Log.e("HomeFragment", "deleteSelectedFiles: 删除线程池无效或已关闭");
            mainHandler.post(() -> showToast("删除服务不可用"));
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
                    
                    // 通知 MediaStore 删除记录
                    if (deleted) {
                        try {
                            android.net.Uri collection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                                : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                            android.content.Context ctx = getContext();
                            if (ctx != null) {
                                ctx.getContentResolver().delete(
                                    collection,
                                    MediaStore.Video.Media.DATA + "=?",
                                    new String[]{file.getAbsolutePath()}
                                );
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "deleteSelectedFiles: 通知MediaStore失败", e);
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
                    showToast(getString(R.string.delete_success) + " (" + finalSuccessCount + ")");
                } else {
                    showToastLong(getString(R.string.delete_success) + ": " + finalSuccessCount + 
                        ", " + getString(R.string.delete_failed) + ": " + finalFailCount);
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
            if (!isAdded() || getContext() == null) return false;
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
                                showToastLong("删除失败：需要用户授权");
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
            if (!isAdded() || getContext() == null) return null;
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
        
        // 清理Handler回调，避免内存泄漏
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        
        // 清理线程池
        if (deleteExecutor != null) {
            deleteExecutor.shutdownNow(); // 使用shutdownNow立即停止
        }
        
        // 移除监听器，避免内存泄漏
        if (pendingDeleteManager != null && pendingDeleteListener != null) {
            pendingDeleteManager.removeOnFileDeletedListener(pendingDeleteListener);
            pendingDeleteListener = null;
        }
        
        // 清除视频列表监听器
        videoListLoadListener = null;
        
        // 清除Adapter中的缓存
        if (adapter != null) {
            adapter.clearCache();
        }
    }
    
    /**
     * 获取视频列表监听器（供 PlayerActivity 使用）
     */
    public static VideoListLoadListener getVideoListLoadListener() {
        return videoListLoadListener;
    }
}


