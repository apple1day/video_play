package androidx.media3.demo.media;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件夹列表Fragment
 */
public class FolderFragment extends Fragment {

    private static final String TAG = "FolderFragment";
    private static final String[] VIDEO_EXTENSIONS = {
        ".mp4", ".avi", ".mkv", ".mov", ".flv", ".wmv", ".3gp", ".m4v", ".webm"
    };

    private RecyclerView folderList;
    private TextView folderCountText;
    private TextView emptyView;
    private View loadingView;
    private SwipeRefreshLayout swipeRefresh;
    private Button refreshButton;
    private Spinner storageSpinner;
    private FolderAdapter adapter;
    private List<FolderItem> folders = new ArrayList<>();

    // 存储设备列表 & 当前筛选
    private List<File> storageDirectories = new ArrayList<>();
    // null = 全部；否则只显示该存储设备下的文件夹
    @Nullable private File currentStorageFilter = null;

    // 内存缓存：避免每次切换Tab都重新扫盘
    private static List<FolderItem> cachedFolders = null;
    private static File cachedStorageFilter = null;  // 缓存对应的存储筛选
    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_folder, container, false);

        folderList = view.findViewById(R.id.folder_list);
        folderCountText = view.findViewById(R.id.folder_count);
        emptyView = view.findViewById(R.id.empty_view);
        loadingView = view.findViewById(R.id.loading_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        refreshButton = view.findViewById(R.id.refresh_button);
        storageSpinner = view.findViewById(R.id.storage_spinner);

        setupRecyclerView();
        setupSwipeRefresh();
        setupRefreshButton();
        setupStorageSpinner();

        // 优先展示缓存，后台异步检查是否需要刷新
        if (cachedFolders != null && java.util.Objects.equals(cachedStorageFilter, currentStorageFilter)) {
            showCachedFolders();
        } else {
            loadFolders();
        }

        return view;
    }

    private void setupRecyclerView() {
        folderList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FolderAdapter(folders, folder -> {
            // 点击文件夹，打开文件夹详情
            openFolderDetail(folder);
        });
        folderList.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            // 下拉刷新时清除缓存，强制重新扫描
            clearCache();
            loadFolders();
        });
    }

    private void setupRefreshButton() {
        refreshButton.setOnClickListener(v -> {
            clearCache();
            loadFolders();
        });
    }

    /**
     * 初始化存储设备下拉框：列出"全部 / 内部存储 / 外部存储(每个独立列出)"。
     * 选项 0 始终是"全部"。
     */
    private void setupStorageSpinner() {
        if (storageSpinner == null) return;
        android.content.Context spinnerCtx = isAdded() && getContext() != null
                ? getContext().getApplicationContext() : null;
        if (spinnerCtx == null) return;
        new Thread(() -> {
            final List<File> dirs = FileUtils.getStorageDirectories(spinnerCtx);
            if (!isAdded() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                storageDirectories = dirs;
                File primary = Environment.getExternalStorageDirectory();
                List<String> labels = new ArrayList<>();
                final List<File> filterRefs = new ArrayList<>(); // 与 labels 一一对应，null=全部
                labels.add(getString(R.string.storage_all_or_fallback_all));
                filterRefs.add(null);
                for (File dir : dirs) {
                    if (dir == null || !dir.exists()) continue;
                    String label;
                    if (dir.equals(primary)) {
                        label = getString(R.string.storage_internal);
                    } else {
                        // 用挂载点最后一段当 U盘/SD卡 显示名（例如 /storage/XXXX-YYYY -> XXXX-YYYY）
                        String tail = dir.getName();
                        label = getString(R.string.storage_external) + (tail.isEmpty() ? "" : " · " + tail);
                    }
                    labels.add(label);
                    filterRefs.add(dir);
                }

                ArrayAdapter<String> a = new ArrayAdapter<>(getContext(),
                        android.R.layout.simple_spinner_item, labels);
                a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                storageSpinner.setAdapter(a);

                storageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        File newFilter = position >= 0 && position < filterRefs.size()
                                ? filterRefs.get(position) : null;
                        if (!java.util.Objects.equals(newFilter, currentStorageFilter)) {
                            currentStorageFilter = newFilter;
                            Log.d(TAG, "切换存储筛选: " + (newFilter == null ? "全部" : newFilter.getAbsolutePath()));
                            clearCache();
                            loadFolders();
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });
            });
        }).start();
    }

    /** 直接展示内存缓存中的文件夹列表，不触发扫描 */
    private void showCachedFolders() {
        folders.clear();
        folders.addAll(cachedFolders);
        adapter.updateFolders(new ArrayList<>(cachedFolders));
        updateEmptyView();
        updateFolderCount();
        Log.d(TAG, "使用缓存展示 " + folders.size() + " 个文件夹");
    }

    /**
     * 加载文件夹列表（带进度显示 + 内存缓存）
     */
    private void loadFolders() {
        if (isLoading) return;
        isLoading = true;
        Log.d(TAG, "开始扫描视频文件夹...");

        // 显示加载中视图（如果列表为空）
        if (folders.isEmpty() && loadingView != null) {
            loadingView.setVisibility(android.view.View.VISIBLE);
            emptyView.setVisibility(android.view.View.GONE);
        }

        // 在后台线程扫描
        android.content.Context appCtx = isAdded() && getContext() != null
                ? getContext().getApplicationContext() : null;
        if (appCtx == null) { isLoading = false; return; }

        final File filterSnapshot = currentStorageFilter;

        new Thread(() -> {
            Map<String, FolderItem> folderMap = new HashMap<>();

            List<File> scanTargets;
            if (filterSnapshot != null) {
                scanTargets = Arrays.asList(filterSnapshot);
            } else {
                scanTargets = FileUtils.getStorageDirectories(appCtx);
            }
            Log.d(TAG, "扫描 " + scanTargets.size() + " 个存储设备，filter="
                    + (filterSnapshot == null ? "全部" : filterSnapshot.getAbsolutePath()));

            for (File storageDir : scanTargets) {
                if (storageDir != null && storageDir.exists()) {
                    Log.d(TAG, "扫描存储设备: " + storageDir.getAbsolutePath());
                    scanDirectory(storageDir, folderMap);
                }
            }

            List<FolderItem> folderResult = new ArrayList<>(folderMap.values());
            folderResult.sort((f1, f2) -> Integer.compare(f2.getVideoCount(), f1.getVideoCount()));

            int totalVideos = 0;
            for (FolderItem folder : folderResult) totalVideos += folder.getVideoCount();
            final int finalTotalVideos = totalVideos;

            // 更新内存缓存
            cachedFolders = new ArrayList<>(folderResult);
            cachedStorageFilter = filterSnapshot;

            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (loadingView != null) loadingView.setVisibility(android.view.View.GONE);
                    folders.clear();
                    folders.addAll(folderResult);
                    adapter.updateFolders(folderResult);
                    updateEmptyView();
                    updateFolderCount();
                    isLoading = false;
                    if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
                        swipeRefresh.setRefreshing(false);
                    }
                    Log.d(TAG, "找到 " + folders.size() + " 个文件夹，共 " + finalTotalVideos + " 个视频");
                });
            } else {
                isLoading = false;
            }
        }).start();
    }

    /**
     * 清除文件夹缓存（在需要强制刷新时调用，例如文件增删后）
     */
    public static void clearCache() {
        cachedFolders = null;
        cachedStorageFilter = null;
    }

    /**
     * 递归扫描目录
     */
    private void scanDirectory(File dir, Map<String, FolderItem> folderMap) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || !dir.canRead()) {
            return;
        }

        // 跳过隐藏目录和系统目录
        String name = dir.getName();
        if (name.startsWith(".") || name.equals("Android")) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        int videoCount = 0;
        long totalSize = 0;

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归扫描子目录
                scanDirectory(file, folderMap);
            } else if (file.isFile() && isVideoFile(file) && !FileUtils.shouldFilterFile(file)) {
                // 只统计未被过滤的视频文件
                videoCount++;
                totalSize += file.length();
            }
        }

        // 如果当前目录有视频文件，添加到文件夹列表
        if (videoCount > 0) {
            String folderPath = dir.getAbsolutePath();
            if (folderMap.containsKey(folderPath)) {
                // 更新已存在的文件夹信息
                FolderItem existing = folderMap.get(folderPath);
                folderMap.put(folderPath, new FolderItem(
                    dir,
                    existing.getVideoCount() + videoCount,
                    existing.getTotalSize() + totalSize
                ));
            } else {
                folderMap.put(folderPath, new FolderItem(dir, videoCount, totalSize));
            }
        }
    }

    /**
     * 判断是否为视频文件
     */
    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 更新空视图
     */
    private void updateEmptyView() {
        if (folders.isEmpty()) {
            folderList.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            folderList.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    /**
     * 更新文件夹数量显示
     */
    private void updateFolderCount() {
        folderCountText.setText(folders.size() + " 个文件夹");
    }

    /**
     * 打开文件夹详情
     */
    private void openFolderDetail(FolderItem folder) {
        Intent intent = new Intent(getContext(), FolderDetailActivity.class);
        intent.putExtra("folder_path", folder.getFolderPath());
        intent.putExtra("folder_name", folder.getFolderName());
        startActivity(intent);
    }

    /**
     * 刷新文件夹列表（外部调用）。外部调用时清除缓存强制重扫。
     */
    public void refreshFolders() {
        clearCache();
        loadFolders();
    }
}
