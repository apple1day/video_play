package androidx.media3.demo.media;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.ArrayList;
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
    private SwipeRefreshLayout swipeRefresh;
    private Button refreshButton;
    private FolderAdapter adapter;
    private List<FolderItem> folders = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_folder, container, false);

        folderList = view.findViewById(R.id.folder_list);
        folderCountText = view.findViewById(R.id.folder_count);
        emptyView = view.findViewById(R.id.empty_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        refreshButton = view.findViewById(R.id.refresh_button);

        setupRecyclerView();
        setupSwipeRefresh();
        setupRefreshButton();

        // 加载文件夹
        loadFolders();

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
            loadFolders();
            swipeRefresh.setRefreshing(false);
        });
    }

    private void setupRefreshButton() {
        refreshButton.setOnClickListener(v -> loadFolders());
    }

    /**
     * 加载文件夹列表
     */
    private void loadFolders() {
        Log.d(TAG, "开始扫描视频文件夹...");

        // 在后台线程扫描
        new Thread(() -> {
            Map<String, FolderItem> folderMap = new HashMap<>();

            // 获取所有存储目录（包括内部存储和外部存储）
            List<File> storageDirectories = FileUtils.getStorageDirectories(getContext());
            Log.d(TAG, "找到 " + storageDirectories.size() + " 个存储设备");
            
            // 扫描所有存储目录
            for (File storageDir : storageDirectories) {
                if (storageDir != null && storageDir.exists()) {
                    Log.d(TAG, "扫描存储设备: " + storageDir.getAbsolutePath());
                    scanDirectory(storageDir, folderMap);
                }
            }

            // 转换为列表
            List<FolderItem> folderList = new ArrayList<>(folderMap.values());

            // 按视频数量排序
            folderList.sort((f1, f2) -> Integer.compare(f2.getVideoCount(), f1.getVideoCount()));

            // 统计总视频数量
            int totalVideos = 0;
            for (FolderItem folder : folderList) {
                totalVideos += folder.getVideoCount();
            }
            
            final int finalTotalVideos = totalVideos;

            // 更新UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    folders.clear();
                    folders.addAll(folderList);
                    adapter.updateFolders(folders);
                    updateEmptyView();
                    updateFolderCount();
                    Log.d(TAG, "找到 " + folders.size() + " 个文件夹，共 " + finalTotalVideos + " 个视频");
                });
            }
        }).start();
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
            } else if (file.isFile() && isVideoFile(file)) {
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
     * 刷新文件夹列表（外部调用）
     */
    public void refreshFolders() {
        loadFolders();
    }
}
