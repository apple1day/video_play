package androidx.media3.demo.media;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 显示手机所有视频文件的 Activity
 */
public class AllVideosActivity extends AppCompatActivity {

    private static final String TAG = "AllVideosActivity";
    
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyView;
    private TextView loadingView;
    private TextView videoCountText;
    private FileAdapter adapter;
    private List<FileItem> allVideos = new ArrayList<>();
    private boolean isGridView = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_videos);

        // 设置 Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.all_videos);
        }

        // 初始化视图
        recyclerView = findViewById(R.id.recycler_view);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        emptyView = findViewById(R.id.empty_view);
        loadingView = findViewById(R.id.loading_view);
        videoCountText = findViewById(R.id.video_count_text);

        // 设置适配器
        adapter = new FileAdapter(new ArrayList<>(), fileItem -> {
            if (fileItem.getFileType() == FileItem.FileType.VIDEO) {
                // 打开播放器
                Intent intent = new Intent(this, PlayerActivity.class);
                String currentPath = fileItem.getFile().getAbsolutePath();
                intent.putExtra("file_path", currentPath);
                
                // 传递视频列表
                ArrayList<String> videoList = new ArrayList<>();
                for (FileItem item : allVideos) {
                    videoList.add(item.getFile().getAbsolutePath());
                }
                intent.putExtra("file_list", videoList);
                
                // 找到当前视频的索引
                int index = videoList.indexOf(currentPath);
                intent.putExtra("current_index", index >= 0 ? index : 0);
                
                startActivity(intent);
            }
        });
        adapter.setContext(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 设置下拉刷新
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        );
        swipeRefreshLayout.setOnRefreshListener(this::loadAllVideos);

        // 检查权限并加载视频
        if (PermissionHelper.hasMediaPermissions(this)) {
            loadAllVideos();
        } else {
            PermissionHelper.requestMediaPermissions(this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            if (PermissionHelper.hasMediaPermissions(this)) {
                loadAllVideos();
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * 加载手机所有视频文件
     */
    private void loadAllVideos() {
        // 显示加载中
        loadingView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        new Thread(() -> {
            // 获取所有存储目录
            List<File> storageDirectories = FileUtils.getStorageDirectories(this);
            
            // 扫描所有视频文件
            List<FileItem> videos = FileUtils.scanAllFiles(storageDirectories, FileItem.FileType.VIDEO);
            
            // 按时间降序排序（最新的在前）
            Collections.sort(videos, (o1, o2) -> 
                Long.compare(o2.getLastModified(), o1.getLastModified()));

            // 更新UI
            new Handler(Looper.getMainLooper()).post(() -> {
                allVideos = videos;
                adapter.updateFiles(videos);
                
                // 更新视频数量
                videoCountText.setText(getString(R.string.videos_found, videos.size()));
                
                // 显示结果
                loadingView.setVisibility(View.GONE);
                if (videos.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
                
                // 停止刷新动画
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从播放器返回时刷新列表（避免显示已删除的视频）
        if (adapter != null && adapter.getItemCount() > 0) {
            loadAllVideos();
        }
    }
}
