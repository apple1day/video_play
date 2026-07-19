package androidx.media3.demo.media;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件夹详情Activity - 显示文件夹内的视频列表
 */
public class FolderDetailActivity extends AppCompatActivity {

    private static final String TAG = "FolderDetailActivity";
    private static final String[] VIDEO_EXTENSIONS = {
        ".mp4", ".avi", ".mkv", ".mov", ".flv", ".wmv", ".3gp", ".m4v", ".webm"
    };

    private TextView folderNameText;
    private TextView folderInfoText;
    private TextView emptyView;
    private RecyclerView videoList;
    private Button backButton;
    private Button sortButton;
    private View selectionToolbarLayout;
    private Button selectAllButton;
    private Button selectFavoritesButton;
    private Button selectPendingDeletesButton;
    private Button deleteSelectedButton;
    private Button cancelSelectionButton;
    private TextView selectedCountText;
    private FileAdapter adapter;
    
    private String folderPath;
    private String folderName;
    private List<FileItem> videos = new ArrayList<>();
    private boolean sortByTime = true; // true=按时间排序，false=按大小排序
    
    private FavoriteManager favoriteManager;
    private PendingDeleteManager pendingDeleteManager;
    private PendingDeleteManager.OnFileDeletedListener pendingDeleteListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_detail);

        // 获取传入的文件夹路径
        folderPath = getIntent().getStringExtra("folder_path");
        folderName = getIntent().getStringExtra("folder_name");

        if (folderPath == null) {
            finish();
            return;
        }

        // 初始化管理器
        favoriteManager = FavoriteManager.getInstance(this);
        pendingDeleteManager = PendingDeleteManager.getInstance(this);
        
        // 设置文件删除监听器（使用 add 方式，不覆盖其他监听器）
        pendingDeleteListener = new PendingDeleteManager.OnFileDeletedListener() {
            @Override
            public void onFileDeleted(String filePath, boolean success, String errorMessage) {
                // 文件删除后刷新列表
                if (success) {
                    runOnUiThread(() -> loadVideos());
                }
            }
            
            @Override
            public void onFilesDeleted(int successCount, int failCount, List<String> failedFiles) {
                // 批量删除后刷新列表
                runOnUiThread(() -> loadVideos());
            }
        };
        pendingDeleteManager.addOnFileDeletedListener(pendingDeleteListener);

        initViews();
        loadVideos();
    }

    private void initViews() {
        folderNameText = findViewById(R.id.folder_name);
        folderInfoText = findViewById(R.id.folder_info);
        emptyView = findViewById(R.id.empty_view);
        videoList = findViewById(R.id.video_list);
        backButton = findViewById(R.id.back_button);
        sortButton = findViewById(R.id.sort_button);
        selectionToolbarLayout = findViewById(R.id.selection_toolbar_layout);
        selectAllButton = findViewById(R.id.select_all_button);
        selectFavoritesButton = findViewById(R.id.select_favorites_button);
        selectPendingDeletesButton = findViewById(R.id.select_pending_deletes_button);
        deleteSelectedButton = findViewById(R.id.delete_selected_button);
        cancelSelectionButton = findViewById(R.id.cancel_selection_button);
        selectedCountText = findViewById(R.id.selected_count_text);

        // 设置文件夹名称
        folderNameText.setText(folderName);

        // 返回按钮
        backButton.setOnClickListener(v -> finish());

        // 排序按钮
        sortButton.setOnClickListener(v -> {
            sortByTime = !sortByTime;
            sortButton.setText(sortByTime ? "时间" : "大小");
            sortVideos();
        });

        // 设置RecyclerView
        videoList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter(videos, item -> {
            // 单击模式
            if (!adapter.isSelectionMode()) {
                // 普通模式：播放视频
                playVideo(item);
            }
            // 多选模式由 FileAdapter 内部处理
        });
        adapter.setContext(this);
        
        // 设置长按监听器：长按进入多选模式或显示菜单
        adapter.setOnItemLongClickListener(item -> {
            // 不使用单独的长按菜单，直接进入多选模式
            // FileAdapter 内部会处理长按进入多选
            return false; // 返回 false 让 FileAdapter 处理
        });
        
        // 设置多选状态监听
        adapter.setSelectionChangeListener(count -> updateSelectionUI());
        
        videoList.setAdapter(adapter);
        
        // 批量操作按钮
        setupSelectionButtons();
    }

    /**
     * 加载文件夹内的视频（增量推送，扫到一个就展示一个，消除白屏）。
     */
    private void loadVideos() {
        Log.d(TAG, "加载文件夹视频: " + folderPath);
        videos.clear();
        adapter.updateFiles(new ArrayList<>());
        emptyView.setVisibility(View.GONE);
        videoList.setVisibility(View.VISIBLE);

        new Thread(() -> {
            File folder = new File(folderPath);
            if (!folder.exists() || !folder.isDirectory()) {
                runOnUiThread(() -> {
                    emptyView.setVisibility(View.VISIBLE);
                    videoList.setVisibility(View.GONE);
                });
                return;
            }

            File[] files = folder.listFiles();
            if (files == null || files.length == 0) {
                runOnUiThread(() -> {
                    emptyView.setVisibility(View.VISIBLE);
                    videoList.setVisibility(View.GONE);
                });
                return;
            }

            long[] totalSize = {0};
            for (File file : files) {
                if (file.isFile() && isVideoFile(file) && !FileUtils.shouldFilterFile(file)) {
                    final FileItem item = new FileItem(file);
                    totalSize[0] += file.length();
                    // 每扫到一个视频立即追加到 UI
                    runOnUiThread(() -> {
                        videos.add(item);
                        adapter.notifyItemInserted(videos.size() - 1);
                        updateEmptyView();
                    });
                }
            }

            // 所有文件扫完后更新 info 文字
            final long finalSize = totalSize[0];
            runOnUiThread(() -> {
                updateFolderInfo(videos.size(), finalSize);
                Log.d(TAG, "加载了 " + videos.size() + " 个视频");
            });
        }).start();
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
     * 排序视频
     */
    private void sortVideos() {
        if (sortByTime) {
            Collections.sort(videos, (o1, o2) -> 
                Long.compare(o2.getLastModified(), o1.getLastModified()));
        } else {
            Collections.sort(videos, (o1, o2) -> 
                Long.compare(o2.getSize(), o1.getSize()));
        }
        adapter.updateFiles(videos);
    }

    /**
     * 更新空视图
     */
    private void updateEmptyView() {
        if (videos.isEmpty()) {
            videoList.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            videoList.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    /**
     * 更新文件夹信息
     */
    private void updateFolderInfo(int count, long size) {
        String info = count + " 个视频  " + FileItem.formatSize(size);
        folderInfoText.setText(info);
    }

    /**
     * 播放视频。
     * 重要：构建的播放列表 = 当前文件夹的视频，**按用户当前排序**展开（与列表显示一致）。
     * 这样从某个视频开始播，会顺着用户看到的顺序自动连播。
     */
    private void playVideo(FileItem item) {
        // 按当前排序后的 videos 顺序构建播放列表（仅保留仍然存在的文件，避免 ExoPlayer 报错）
        ArrayList<String> fileList = new ArrayList<>();
        int currentIndex = 0;
        String targetPath = item.getFile().getAbsolutePath();

        for (FileItem video : videos) {
            File f = video.getFile();
            if (f == null || !f.exists()) continue;
            String path = f.getAbsolutePath();
            if (path.equals(targetPath)) {
                currentIndex = fileList.size(); // 在过滤后列表里的真实索引
            }
            fileList.add(path);
        }

        if (fileList.isEmpty()) {
            Toast.makeText(this, "当前文件夹没有可播放的视频", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "构建文件夹播放列表: 总数=" + fileList.size() + ", 起始索引=" + currentIndex
                + ", 排序=" + (sortByTime ? "时间" : "大小"));

        // 启动播放器
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("file_path", targetPath);
        intent.putStringArrayListExtra("file_list", fileList);
        intent.putExtra("current_index", currentIndex);
        // 标记此播放列表来自"文件夹详情"，PlayerActivity 不应触发 HomeFragment 的"加载更多"逻辑
        intent.putExtra("from_folder", true);
        intent.putExtra("folder_path", folderPath);
        startActivity(intent);
    }
    
    /**
     * 显示视频选项对话框（长按菜单）
     */
    private void showVideoOptionsDialog(FileItem item) {
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
        
        new AlertDialog.Builder(this)
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
                        confirmAndDeleteVideo(item);
                        break;
                    case 3: // 查看信息
                        showVideoInfo(item);
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
        showTopToast(message);
        adapter.notifyDataSetChanged();
    }
    
    /**
     * 切换待删除状态
     */
    private void togglePendingDelete(String filePath) {
        boolean isPending = pendingDeleteManager.togglePendingDelete(filePath);
        String message = isPending ? "已标记为待删除" : "已取消待删除";
        showTopToast(message);
        adapter.notifyDataSetChanged();
    }
    
    /**
     * 显示视频信息
     */
    private void showVideoInfo(FileItem item) {
        String info = "文件名: " + item.getName() + "\n" +
                     "大小: " + item.getFormattedSize() + "\n" +
                     "路径: " + item.getFile().getAbsolutePath() + "\n" +
                     "修改时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                         .format(new java.util.Date(item.getLastModified()));
        
        new AlertDialog.Builder(this)
            .setTitle("视频信息")
            .setMessage(info)
            .setPositiveButton("确定", null)
            .show();
    }
    
    /**
     * 确认并删除视频
     */
    private void confirmAndDeleteVideo(FileItem item) {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除 \"" + item.getName() + "\" 吗？\n此操作无法撤销。")
            .setPositiveButton("删除", (dialog, which) -> deleteVideoAsync(item))
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 异步删除视频
     */
    private void deleteVideoAsync(FileItem item) {
        File file = item.getFile();
        String fileName = file.getName();
        
        // 显示删除中提示
        showTopToast("正在删除 " + fileName + "...");
        
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
                                // 文件仍然存在，尝试多次删除0字节文件
                                if (file.length() == 0) {
                                    for (int i = 0; i < 3; i++) {
                                        success = file.delete();
                                        try {
                                            Thread.sleep(50);
                                        } catch (InterruptedException e) {
                                            // 忽略
                                        }
                                        if (!file.exists()) {
                                            break;
                                        }
                                    }
                                    
                                    // 如果还是删不掉，尝试清空文件再删除
                                    if (file.exists()) {
                                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                                            fos.getChannel().truncate(0);
                                            fos.flush();
                                        } catch (Exception e) {
                                            Log.w(TAG, "清空文件失败", e);
                                        }
                                        success = file.delete();
                                    }
                                } else {
                                    // 不是0字节文件，再尝试删除一次
                                    success = file.delete();
                                }
                                
                                if (file.exists()) {
                                    success = false;
                                    errorMessage = "删除失败（文件仍然存在）";
                                }
                            }
                            
                            // 通知媒体扫描器更新
                            if (success) {
                                try {
                                    android.media.MediaScannerConnection.scanFile(
                                        this,
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
            
            runOnUiThread(() -> {
                if (finalSuccess) {
                    showTopToast("✓ 已删除: " + fileName);
                    // 从收藏和待删除列表中移除
                    favoriteManager.removeFavorite(file.getAbsolutePath());
                    pendingDeleteManager.removePendingDelete(file.getAbsolutePath());
                    // 刷新列表
                    loadVideos();
                } else {
                    showTopToast("✗ 删除失败: " + fileName + "\n" + finalError);
                }
            });
        }).start();
    }
    
    /**
     * 显示顶部Toast提示
     */
    private void showTopToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 100);
        toast.show();
    }
    
    /**
     * 设置批量操作按钮
     */
    private void setupSelectionButtons() {
        selectAllButton.setOnClickListener(v -> {
            int currentCount = adapter.getSelectedCount();
            int totalCount = adapter.getItemCount();
            
            if (currentCount == totalCount && totalCount > 0) {
                // 全部已选中，取消全选
                adapter.clearSelection();
            } else {
                // 全选
                adapter.selectAll();
            }
        });
        
        selectFavoritesButton.setOnClickListener(v -> {
            // 选择所有收藏的视频
            List<Integer> favoritePositions = new ArrayList<>();
            for (int i = 0; i < videos.size(); i++) {
                if (favoriteManager.isFavorite(videos.get(i).getFile().getAbsolutePath())) {
                    favoritePositions.add(i);
                }
            }
            if (!favoritePositions.isEmpty()) {
                adapter.selectPositions(favoritePositions);
                showTopToast("已选择 " + favoritePositions.size() + " 个收藏视频");
            } else {
                showTopToast("没有收藏的视频");
            }
        });
        
        selectPendingDeletesButton.setOnClickListener(v -> {
            // 选择所有待删除的视频
            List<Integer> pendingPositions = new ArrayList<>();
            for (int i = 0; i < videos.size(); i++) {
                if (pendingDeleteManager.isPendingDelete(videos.get(i).getFile().getAbsolutePath())) {
                    pendingPositions.add(i);
                }
            }
            if (!pendingPositions.isEmpty()) {
                adapter.selectPositions(pendingPositions);
                showTopToast("已选择 " + pendingPositions.size() + " 个待删除视频");
            } else {
                showTopToast("没有待删除的视频");
            }
        });
        
        deleteSelectedButton.setOnClickListener(v -> {
            confirmAndDeleteSelected();
        });
        
        cancelSelectionButton.setOnClickListener(v -> {
            adapter.setSelectionMode(false);
        });
    }
    
    /**
     * 更新批量操作UI
     */
    private void updateSelectionUI() {
        if (adapter.isSelectionMode()) {
            selectionToolbarLayout.setVisibility(View.VISIBLE);
            int count = adapter.getSelectedCount();
            int total = adapter.getItemCount();
            selectedCountText.setText("已选 " + count + " 项");
            deleteSelectedButton.setEnabled(count > 0);
            
            // 更新全选按钮文本
            if (count == total && total > 0) {
                selectAllButton.setText("取消全选");
            } else {
                selectAllButton.setText("全选");
            }
        } else {
            selectionToolbarLayout.setVisibility(View.GONE);
        }
    }
    
    /**
     * 确认并批量删除
     */
    private void confirmAndDeleteSelected() {
        List<FileItem> selectedItems = adapter.getSelectedItems();
        if (selectedItems.isEmpty()) {
            showTopToast("请先选择要删除的视频");
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle("确认批量删除")
            .setMessage("确定要删除选中的 " + selectedItems.size() + " 个视频吗？\n此操作无法撤销。")
            .setPositiveButton("删除", (dialog, which) -> {
                deleteSelectedAsync(selectedItems);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 异步批量删除
     */
    private void deleteSelectedAsync(List<FileItem> itemsToDelete) {
        adapter.setSelectionMode(false);
        
        int totalCount = itemsToDelete.size();
        showTopToast("正在删除 " + totalCount + " 个视频...");
        
        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            
            for (FileItem item : itemsToDelete) {
                File file = item.getFile();
                boolean success = false;
                
                try {
                    if (file.exists()) {
                        success = file.delete();
                        
                        if (success) {
                            // 优化：减少等待时间至30ms（原50ms）
                            Thread.sleep(30);
                            
                            // 检查并处理0字节文件
                            if (file.exists()) {
                                if (file.length() == 0) {
                                    // 优化：减少重试次数和等待时间（原来3次，每次30ms）
                                    for (int i = 0; i < 2; i++) {
                                        success = file.delete();
                                        Thread.sleep(20);
                                        if (!file.exists()) break;
                                    }
                                    
                                    // 如果仍然存在，尝试清空并删除
                                    if (file.exists()) {
                                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                                            fos.getChannel().truncate(0);
                                            fos.flush();
                                        }
                                        success = file.delete();
                                    }
                                } else {
                                    success = file.delete();
                                }
                            }
                        }
                        
                        if (success || !file.exists()) {
                            successCount++;
                            // 从管理器中移除
                            favoriteManager.removeFavorite(file.getAbsolutePath());
                            pendingDeleteManager.removePendingDelete(file.getAbsolutePath());
                        } else {
                            failCount++;
                        }
                    }
                } catch (Exception e) {
                    failCount++;
                    Log.e(TAG, "批量删除失败: " + file.getName(), e);
                }
            }
            
            final int finalSuccess = successCount;
            final int finalFail = failCount;
            
            runOnUiThread(() -> {
                String message = "删除完成：成功 " + finalSuccess + " 个";
                if (finalFail > 0) {
                    message += "，失败 " + finalFail + " 个";
                }
                showTopToast(message);
                
                // 刷新列表
                loadVideos();
            });
        }).start();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 移除监听器，避免内存泄漏
        if (pendingDeleteManager != null && pendingDeleteListener != null) {
            pendingDeleteManager.removeOnFileDeletedListener(pendingDeleteListener);
            pendingDeleteListener = null;
        }
        
        // 清除Adapter中的缓存
        if (adapter != null) {
            adapter.clearCache();
        }
    }
}
