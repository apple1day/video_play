package com.anxiong.mv;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 主活动，显示本地视频文件列表
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String TAG = "MainActivity";
    
    private RecyclerView videoRecyclerView;
    private ProgressBar loadingProgress;
    private TextView noVideosText;
    private VideoAdapter videoAdapter;
    private List<VideoItem> videoList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbar));
        
        videoRecyclerView = findViewById(R.id.video_list);
        loadingProgress = findViewById(R.id.loading_progress);
        noVideosText = findViewById(R.id.no_videos_text);

        // 设置RecyclerView的布局管理器
        videoRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // 创建并设置适配器
        videoAdapter = new VideoAdapter(this, videoList);
        videoRecyclerView.setAdapter(videoAdapter);
        
        // 设置点击监听器
        videoAdapter.setOnItemClickListener((position, videoItem) -> {
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            intent.putExtra(PlayerActivity.EXTRA_VIDEO_POSITION, position);
            intent.putExtra(PlayerActivity.EXTRA_VIDEO_LIST, videoList.toArray(new VideoItem[0]));
            startActivity(intent);
        });
        
        // 检查存储权限
        checkStoragePermission();
    }

    /**
     * 检查存储权限
     */
    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        } else {
            // 权限已授予，扫描视频
            scanVideos();
        }
    }

    /**
     * 处理权限请求结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，扫描视频
                scanVideos();
            } else {
                // 权限被拒绝，显示提示
                Toast.makeText(this, "需要存储权限才能访问视频文件", Toast.LENGTH_SHORT).show();
                noVideosText.setVisibility(View.VISIBLE);
                noVideosText.setText("需要存储权限才能访问视频文件");
            }
        }
    }

    /**
     * 扫描本地视频文件
     */
    private void scanVideos() {
        loadingProgress.setVisibility(View.VISIBLE);
        noVideosText.setVisibility(View.GONE);
        videoRecyclerView.setVisibility(View.GONE);

        // 使用AsyncTask扫描视频
        new AsyncTask<Void, Void, List<VideoItem>>() {
            @Override
            protected List<VideoItem> doInBackground(Void... params) {
                return scanLocalVideos();
            }
            
            @Override
            protected void onPostExecute(List<VideoItem> videos) {
                videoList.clear();
                videoList.addAll(videos);
                videoAdapter.notifyDataSetChanged();
                
                loadingProgress.setVisibility(View.GONE);
                if (videoList.isEmpty()) {
                    noVideosText.setVisibility(View.VISIBLE);
                    videoRecyclerView.setVisibility(View.GONE);
                } else {
                    noVideosText.setVisibility(View.GONE);
                    videoRecyclerView.setVisibility(View.VISIBLE);
                }
            }
        }.execute();
    }

    /**
     * 扫描本地视频文件
     */
    private List<VideoItem> scanLocalVideos() {
        List<VideoItem> videos = new ArrayList<>();
        ContentResolver contentResolver = getContentResolver();
        
        // 定义要查询的媒体列
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_MODIFIED
        };
        
        // 排序方式：按修改日期降序
        String sortOrder = MediaStore.Video.Media.DATE_MODIFIED + " DESC";
        
        try (Cursor cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder)) {
            
            if (cursor != null && cursor.moveToFirst()) {
                // 获取列索引
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                int pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
                
                do {
                    // 提取视频信息
                    long id = cursor.getLong(idIndex);
                    String title = cursor.getString(titleIndex);
                    String path = cursor.getString(pathIndex);
                    String uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)).toString();
                    long duration = cursor.getLong(durationIndex);
                    long dateModified = cursor.getLong(dateModifiedIndex);
                    
                    // 创建VideoItem并添加到列表
                    videos.add(new VideoItem(id, title, path, uri, duration, dateModified));
                    
                } while (cursor.moveToNext());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "扫描视频失败", e);
        }
        
        return videos;
    }



    /**
     * 视频列表适配器
     */
    private static class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.ViewHolder> {
        
        private final Context context;
        private final List<VideoItem> videoList;
        private OnItemClickListener onItemClickListener;
        
        interface OnItemClickListener {
            void onItemClick(int position, VideoItem videoItem);
        }
        
        public void setOnItemClickListener(OnItemClickListener listener) {
            this.onItemClickListener = listener;
        }
        
        public VideoAdapter(Context context, List<VideoItem> videoList) {
            this.context = context;
            this.videoList = videoList;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.video_list_item, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VideoItem videoItem = videoList.get(position);
            holder.videoTitle.setText(videoItem.getTitle());
            holder.videoPath.setText(videoItem.getPath());
        }
        
        @Override
        public int getItemCount() {
            return videoList.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView videoTitle;
            TextView videoPath;
            
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                videoTitle = itemView.findViewById(R.id.video_title);
                videoPath = itemView.findViewById(R.id.video_path);
                
                itemView.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && onItemClickListener != null) {
                        onItemClickListener.onItemClick(position, videoList.get(position));
                    }
                });
            }
        }
    }

}

