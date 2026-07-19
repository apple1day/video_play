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
            List<File> scanTargets;
            if (filterSnapshot != null) {
                scanTargets = Arrays.asList(filterSnapshot);
            } else {
                scanTargets = FileUtils.getStorageDirectories(appCtx);
            }

            for (File storageDir : scanTargets) {
                if (storageDir == null || !storageDir.exists()) continue;
                // 增量扫描：每扫完一个一级子目录就推一次 UI
                scanDirectoryIncremental(storageDir, filterSnapshot);
            }

            // 扫描完成，更新缓存
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (loadingView != null) loadingView.setVisibility(android.view.View.GONE);
                    cachedFolders = new ArrayList<>(folders);
                    cachedStorageFilter = filterSnapshot;
                    updateFolderCount();
                    isLoading = false;
                    if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
                        swipeRefresh.setRefreshing(false);
                    }
                    updateEmptyView();
                    Log.d(TAG, "扫描完成，共 " + folders.size() + " 个文件夹");
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
     * 增量扫描根存储目录下的每个一级子目录，每扫到有视频的文件夹就立即推到 UI。
     */
    private void scanDirectoryIncremental(File root, File filterSnapshot) {
        if (root == null || !root.exists() || !root.canRead()) return;
        File[] topDirs = root.listFiles(f -> !f.getName().startsWith(".") && !f.getName().equals("Android") && f.isDirectory());
        if (topDirs == null) {
            // root 本身直接包含视频文件
            scanAndPushFolder(root);
            return;
        }
        for (File topDir : topDirs) {
            scanAndPushFolderRecursive(topDir);
        }
        // 扫描 root 层直接包含的视频文件
        scanAndPushFolder(root);
    }

    private void scanAndPushFolderRecursive(File dir) {
        if (dir == null || !dir.exists() || !dir.canRead()) return;
        scanAndPushFolder(dir);
        File[] subDirs = dir.listFiles(f -> !f.getName().startsWith(".") && f.isDirectory());
        if (subDirs != null) {
            for (File sub : subDirs) scanAndPushFolderRecursive(sub);
        }
    }

    /** 扫描单个目录（不递归），若有视频立即推到 UI。 */
    private void scanAndPushFolder(File dir) {
        if (dir == null || !dir.exists() || !dir.canRead()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        int videoCount = 0;
        long totalSize = 0;
        for (File f : files) {
            if (f.isFile() && isVideoFile(f) && !FileUtils.shouldFilterFile(f)) {
                videoCount++;
                totalSize += f.length();
            }
        }
        if (videoCount == 0) return;

        final FolderItem newItem = new FolderItem(dir, videoCount, totalSize);
        if (isAdded() && getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                // 检查是否已有此文件夹（避免重复）
                String path = dir.getAbsolutePath();
                for (int i = 0; i < folders.size(); i++) {
                    if (folders.get(i).getFolderPath().equals(path)) return;
                }
                folders.add(newItem);
                adapter.notifyItemInserted(folders.size() - 1);
                if (loadingView != null) loadingView.setVisibility(android.view.View.GONE);
                updateEmptyView();
                updateFolderCount();
            });
        }
    }

    /**
     * 递归扫描目录（保留原方法，供未来使用）
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
