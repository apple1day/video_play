# Media 项目代码汇总

生成时间: 1767766386.2416804

源目录: D:\zcode\anxiong\myLog\media\media

文件数量: 23

---


## 统计信息

- 总文件数: 23

- 总大小: 136,887 bytes (0.13 MB)

- 文件类型分布:

  - java: 13 个文件

  - xml: 9 个文件

  - markdown: 1 个文件


---

## README.md

<!-- 大小: 1,350 bytes -->

```markdown
# File Browser Demo

这是一个文件浏览器和媒体播放器示例应用，展示了如何使用 AndroidX Media3 (ExoPlayer) 进行视频播放。

## 功能特性

1. **双 Tab 界面**
   - 首页：显示所有媒体文件
   - 更多：文件浏览器和存储信息

2. **首页功能**
   - 扫描本地存储和外置存储的所有文件
   - 支持按类型筛选（视频/音乐/文档/全部）
   - 支持按时间或大小排序
   - 点击视频文件可播放

3. **更多 Tab 功能**
   - 浏览本地存储和外置存储目录
   - 显示存储空间信息（总空间、已用空间、可用空间）
   - 文件夹-文件模式浏览

4. **视频播放功能**
   - 使用 ExoPlayer 播放视频
   - 左侧删除按钮
   - 删除后自动播放下一个视频
   - 如果没有下一个视频，自动关闭播放器

## 权限要求

- Android 13+ (API 33+): READ_MEDIA_VIDEO, READ_MEDIA_AUDIO
- Android 10-12 (API 29-32): READ_EXTERNAL_STORAGE
- Android 9 及以下: READ_EXTERNAL_STORAGE

## 使用方法

1. 运行应用
2. 授予存储权限
3. 在首页浏览和筛选文件
4. 点击视频文件进行播放
5. 在播放界面可以删除当前视频

## 技术栈

- AndroidX Media3 (ExoPlayer)
- Material Design Components
- ViewPager2 + TabLayout
- RecyclerView


```

## src/main/AndroidManifest.xml

<!-- 大小: 4,011 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Copyright (C) 2016 The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
-->

<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="com.google.android.gms.permission.AD_ID" />
    <uses-permission android:name="com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_ATTRIBUTION" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_TOPICS" />
    <uses-permission android:name="com.huawei.appmarket.service.commondata.permission.GET_COMMON_DATA" />
    <uses-permission android:name="com.android.vending.BILLING" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="com.google.android.providers.gsf.permission.READ_GSERVICES" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="com.storytellerstudio.knightstour.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
    
    <!-- 保留原有的文件访问权限 -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28"
        tools:ignore="ScopedStorage" />
    <!-- Android 11+ 管理所有文件权限（可选，用于删除任意文件） -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />

    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:allowBackup="false"
        android:supportsRtl="true"
        tools:targetApi="29">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.AppCompat.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".PlayerActivity"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode"
            android:launchMode="singleTop"
            android:theme="@style/PlayerTheme"
            android:exported="false" />

        <activity
            android:name=".SettingsActivity"
            android:theme="@style/Theme.AppCompat.Light"
            android:exported="false" />

    </application>

</manifest>


```

## src/main/java/androidx/media3/demo/media/FavoriteManager.java

<!-- 大小: 2,506 bytes -->

```java
package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 收藏管理器 - 使用 SharedPreferences 存储收藏的视频
 */
public class FavoriteManager {
    
    private static final String PREF_NAME = "favorites";
    private static final String KEY_FAVORITE_FILES = "favorite_files";
    
    private static FavoriteManager instance;
    private SharedPreferences preferences;
    
    private FavoriteManager(Context context) {
        preferences = context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized FavoriteManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoriteManager(context);
        }
        return instance;
    }
    
    /**
     * 添加收藏
     */
    public void addFavorite(String filePath) {
        Set<String> favorites = getFavorites();
        favorites.add(filePath);
        saveFavorites(favorites);
    }
    
    /**
     * 移除收藏
     */
    public void removeFavorite(String filePath) {
        Set<String> favorites = getFavorites();
        favorites.remove(filePath);
        saveFavorites(favorites);
    }
    
    /**
     * 切换收藏状态
     * @return 切换后的状态：true = 已收藏，false = 未收藏
     */
    public boolean toggleFavorite(String filePath) {
        if (isFavorite(filePath)) {
            removeFavorite(filePath);
            return false;
        } else {
            addFavorite(filePath);
            return true;
        }
    }
    
    /**
     * 检查是否已收藏
     */
    public boolean isFavorite(String filePath) {
        return getFavorites().contains(filePath);
    }
    
    /**
     * 获取所有收藏
     */
    public Set<String> getFavorites() {
        return new HashSet<>(preferences.getStringSet(KEY_FAVORITE_FILES, new HashSet<>()));
    }
    
    /**
     * 保存收藏列表
     */
    private void saveFavorites(Set<String> favorites) {
        preferences.edit()
            .putStringSet(KEY_FAVORITE_FILES, favorites)
            .apply();
    }
    
    /**
     * 清空所有收藏
     */
    public void clearAllFavorites() {
        preferences.edit()
            .remove(KEY_FAVORITE_FILES)
            .apply();
    }
}

```

## src/main/java/androidx/media3/demo/media/FileAdapter.java

<!-- 大小: 12,358 bytes -->

```java
package androidx.media3.demo.media;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private List<FileItem> files = new ArrayList<>();
    private OnItemClickListener listener;
    private OnSelectionChangeListener selectionChangeListener;
    private boolean isSelectionMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();
    private FavoriteManager favoriteManager;
    private PendingDeleteManager pendingDeleteManager;
    private Context context;

    public interface OnItemClickListener {
        void onItemClick(FileItem item);
    }
    
    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }
    
    public void setSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    public FileAdapter(List<FileItem> files, OnItemClickListener listener) {
        this.files = files;
        this.listener = listener;
    }
    
    public void setContext(Context context) {
        this.context = context;
        this.favoriteManager = FavoriteManager.getInstance(context);
        this.pendingDeleteManager = PendingDeleteManager.getInstance(context);
    }

    public void updateFiles(List<FileItem> newFiles) {
        this.files = newFiles;
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public List<FileItem> getFiles() {
        return files;
    }
    
    public void setSelectionMode(boolean enabled) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled;
            if (!enabled) {
                selectedPositions.clear();
            }
            notifyDataSetChanged();
            if (selectionChangeListener != null) {
                selectionChangeListener.onSelectionChanged(selectedPositions.size());
            }
        }
    }
    
    public boolean isSelectionMode() {
        return isSelectionMode;
    }
    
    public void toggleSelection(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        notifyItemChanged(position);
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }
    
    public void selectAll() {
        selectedPositions.clear();
        for (int i = 0; i < files.size(); i++) {
            selectedPositions.add(i);
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }
    
    /**
     * 选择所有收藏的文件
     */
    public void selectAllFavorites() {
        // 确保 favoriteManager 已初始化
        if (favoriteManager == null && context != null) {
            favoriteManager = FavoriteManager.getInstance(context);
        }
        
        if (favoriteManager == null) {
            return;
        }
        
        selectedPositions.clear();
        for (int i = 0; i < files.size(); i++) {
            FileItem item = files.get(i);
            if (item.getFileType() == FileItem.FileType.VIDEO) {
                String filePath = item.getFile().getAbsolutePath();
                if (favoriteManager.isFavorite(filePath)) {
                    selectedPositions.add(i);
                }
            }
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }
    
    /**
     * 选择所有待删除的文件
     */
    public void selectAllPendingDeletes() {
        // 确保 pendingDeleteManager 已初始化
        if (pendingDeleteManager == null && context != null) {
            pendingDeleteManager = PendingDeleteManager.getInstance(context);
        }
        
        if (pendingDeleteManager == null) {
            return;
        }
        
        selectedPositions.clear();
        for (int i = 0; i < files.size(); i++) {
            FileItem item = files.get(i);
            if (item.getFileType() == FileItem.FileType.VIDEO) {
                String filePath = item.getFile().getAbsolutePath();
                if (pendingDeleteManager.isPendingDelete(filePath)) {
                    selectedPositions.add(i);
                }
            }
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
        }
    }
    
    public void clearSelection() {
        selectedPositions.clear();
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(0);
        }
    }
    
    public List<FileItem> getSelectedItems() {
        List<FileItem> selected = new ArrayList<>();
        for (Integer position : selectedPositions) {
            if (position >= 0 && position < files.size()) {
                selected.add(files.get(position));
            }
        }
        return selected;
    }
    
    public int getSelectedCount() {
        return selectedPositions.size();
    }
    
    public boolean isSelected(int position) {
        return selectedPositions.contains(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        favoriteManager = FavoriteManager.getInstance(context);
        pendingDeleteManager = PendingDeleteManager.getInstance(context);
        View view = LayoutInflater.from(context)
            .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = files.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView icon;
        private TextView fileName;
        private TextView fileInfo;
        private CheckBox checkBox;
        private ImageView favoriteIcon;
        private ImageView pendingDeleteIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileInfo = itemView.findViewById(R.id.file_info);
            checkBox = itemView.findViewById(R.id.checkbox);
            favoriteIcon = itemView.findViewById(R.id.favorite_icon);
            pendingDeleteIcon = itemView.findViewById(R.id.pending_delete_icon);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    if (isSelectionMode) {
                        // 多选模式下，切换选中状态
                        toggleSelection(position);
                    } else {
                        // 普通模式下，点击打开文件
                        if (listener != null) {
                            listener.onItemClick(files.get(position));
                        }
                    }
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && !isSelectionMode) {
                    // 长按进入多选模式
                    setSelectionMode(true);
                    toggleSelection(position);
                    return true;
                }
                return false;
            });
        }

        public void bind(FileItem item, int position) {
            fileName.setText(item.getName());
            
            // 设置图标
            if (item.isDirectory()) {
                // 使用备用图标，因为 ic_menu_folder 在新版本 Android 中可能不存在
                icon.setImageResource(android.R.drawable.ic_dialog_info);
            } else {
                switch (item.getFileType()) {
                    case VIDEO:
                        icon.setImageResource(android.R.drawable.ic_media_play);
                        break;
                    case AUDIO:
                        icon.setImageResource(android.R.drawable.ic_media_play);
                        break;
                    case DOCUMENT:
                        icon.setImageResource(android.R.drawable.ic_menu_info_details);
                        break;
                    default:
                        icon.setImageResource(android.R.drawable.ic_menu_gallery);
                        break;
                }
            }
            
            // 设置文件信息
            StringBuilder info = new StringBuilder();
            if (!item.isDirectory()) {
                info.append(item.getFormattedSize());
            }
            if (info.length() > 0) {
                info.append(" • ");
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            info.append(sdf.format(new Date(item.getLastModified())));
            fileInfo.setText(info.toString());
            
            // 设置收藏图标
            if (favoriteIcon != null && favoriteManager != null) {
                if (item.getFileType() == FileItem.FileType.VIDEO) {
                    String filePath = item.getFile().getAbsolutePath();
                    boolean isFavorited = favoriteManager.isFavorite(filePath);
                    if (isFavorited) {
                        favoriteIcon.setVisibility(View.VISIBLE);
                        favoriteIcon.setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN);
                    } else {
                        favoriteIcon.setVisibility(View.GONE);
                    }
                } else {
                    favoriteIcon.setVisibility(View.GONE);
                }
            }
            
            // 设置待删除图标
            if (pendingDeleteIcon != null && pendingDeleteManager != null) {
                if (item.getFileType() == FileItem.FileType.VIDEO) {
                    String filePath = item.getFile().getAbsolutePath();
                    boolean isPending = pendingDeleteManager.isPendingDelete(filePath);
                    if (isPending) {
                        pendingDeleteIcon.setVisibility(View.VISIBLE);
                        pendingDeleteIcon.setColorFilter(Color.parseColor("#FF0000"), PorterDuff.Mode.SRC_IN);
                    } else {
                        pendingDeleteIcon.setVisibility(View.GONE);
                    }
                } else {
                    pendingDeleteIcon.setVisibility(View.GONE);
                }
            }
            
            // 设置多选模式
            if (checkBox != null) {
                if (isSelectionMode) {
                    checkBox.setVisibility(View.VISIBLE);
                    checkBox.setChecked(selectedPositions.contains(position));
                } else {
                    checkBox.setVisibility(View.GONE);
                }
            }
        }
    }
}


```

## src/main/java/androidx/media3/demo/media/FileBrowserAdapter.java

<!-- 大小: 3,973 bytes -->

```java
package androidx.media3.demo.media;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileBrowserAdapter extends RecyclerView.Adapter<FileBrowserAdapter.ViewHolder> {

    private List<FileItem> files = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(FileItem item);
    }

    public FileBrowserAdapter(List<FileItem> files, OnItemClickListener listener) {
        this.files = files;
        this.listener = listener;
    }

    public void updateFiles(List<FileItem> newFiles) {
        this.files = newFiles;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileItem item = files.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView icon;
        private TextView fileName;
        private TextView fileInfo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileInfo = itemView.findViewById(R.id.file_info);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(files.get(position));
                }
            });
        }

        public void bind(FileItem item) {
            fileName.setText(item.getName());
            
            if (item.isDirectory()) {
                // 使用文件夹图标
                icon.setImageResource(android.R.drawable.ic_menu_view);
                // 显示文件夹信息：修改时间
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                fileInfo.setText("文件夹 • " + sdf.format(new Date(item.getLastModified())));
            } else {
                // 根据文件类型显示不同图标
                switch (item.getFileType()) {
                    case VIDEO:
                        icon.setImageResource(android.R.drawable.ic_media_play);
                        break;
                    case AUDIO:
                        icon.setImageResource(android.R.drawable.ic_media_ff);
                        break;
                    case DOCUMENT:
                        icon.setImageResource(android.R.drawable.ic_menu_edit);
                        break;
                    default:
                        icon.setImageResource(android.R.drawable.ic_menu_gallery);
                        break;
                }
                
                StringBuilder info = new StringBuilder();
                info.append(item.getFormattedSize());
                info.append(" • ");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                info.append(sdf.format(new Date(item.getLastModified())));
                fileInfo.setText(info.toString());
            }
        }
    }
}


```

## src/main/java/androidx/media3/demo/media/FileItem.java

<!-- 大小: 2,719 bytes -->

```java
package androidx.media3.demo.media;

import java.io.File;

public class FileItem {
    private final File file;
    private final String name;
    private final long size;
    private final long lastModified;
    private final boolean isDirectory;
    private final FileType fileType;

    public enum FileType {
        VIDEO, AUDIO, DOCUMENT, OTHER
    }

    public FileItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.size = file.length();
        this.lastModified = file.lastModified();
        this.isDirectory = file.isDirectory();
        this.fileType = determineFileType(file);
    }

    private FileType determineFileType(File file) {
        if (file.isDirectory()) {
            return FileType.OTHER;
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv") ||
            name.endsWith(".mov") || name.endsWith(".wmv") || name.endsWith(".flv") ||
            name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".m4v")) {
            return FileType.VIDEO;
        } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") ||
                   name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".m4a") ||
                   name.endsWith(".wma")) {
            return FileType.AUDIO;
        } else if (name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") ||
                   name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".ppt") ||
                   name.endsWith(".pptx") || name.endsWith(".txt") || name.endsWith(".rtf")) {
            return FileType.DOCUMENT;
        }
        return FileType.OTHER;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public FileType getFileType() {
        return fileType;
    }

    public String getFormattedSize() {
        if (isDirectory) {
            return "";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}


```

## src/main/java/androidx/media3/demo/media/FileUtils.java

<!-- 大小: 8,070 bytes -->

```java
package androidx.media3.demo.media;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    
    public static List<File> getStorageDirectories(Context context) {
        List<File> directories = new ArrayList<>();
        
        // 内部存储（应用数据目录的父目录，通常是 /data）
        File internalStorage = Environment.getDataDirectory();
        if (internalStorage != null && internalStorage.exists()) {
            directories.add(internalStorage);
        }
        
        // 外部存储（包括U盘等）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directories.addAll(getStorageDirectoriesApi30(context));
        } else {
            // Android 10 及以下
            File externalStorage = Environment.getExternalStorageDirectory();
            if (externalStorage != null && externalStorage.exists()) {
                directories.add(externalStorage);
            }
            
            // 尝试获取其他存储卷（U盘等）
            directories.addAll(getRemovableStorageDirectories(context));
        }
        
        return directories;
    }
    
    @RequiresApi(api = Build.VERSION_CODES.R)
    private static List<File> getStorageDirectoriesApi30(Context context) {
        List<File> directories = new ArrayList<>();
        StorageManager storageManager = context.getSystemService(StorageManager.class);
        if (storageManager != null) {
            List<StorageVolume> volumes = storageManager.getStorageVolumes();
            for (StorageVolume volume : volumes) {
                if (volume.getState().equals(Environment.MEDIA_MOUNTED)) {
                    File directory = volume.getDirectory();
                    if (directory != null && directory.exists()) {
                        directories.add(directory);
                    }
                }
            }
        }
        return directories;
    }
    
    /**
     * 获取可移动存储设备（U盘等）的目录
     * 适用于 Android 10 及以下版本
     */
    private static List<File> getRemovableStorageDirectories(Context context) {
        List<File> directories = new ArrayList<>();
        
        // 常见的可移动存储路径
        File storageDir = new File("/storage");
        if (storageDir.exists() && storageDir.isDirectory()) {
            File[] files = storageDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    // 排除内部存储和外部存储的标准路径
                    String name = file.getName();
                    if (!name.equals("emulated") && !name.equals("self") && file.isDirectory()) {
                        // 检查是否是挂载的存储设备
                        File[] subFiles = file.listFiles();
                        if (subFiles != null && subFiles.length > 0) {
                            directories.add(file);
                        }
                    }
                }
            }
        }
        
        // 也检查 /mnt 目录（某些设备的挂载点）
        File mntDir = new File("/mnt");
        if (mntDir.exists() && mntDir.isDirectory()) {
            File[] files = mntDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory() && file.canRead()) {
                        // 检查是否是外部存储设备
                        String path = file.getAbsolutePath();
                        if (path.contains("sdcard") || path.contains("usb") || 
                            path.contains("external")) {
                            directories.add(file);
                        }
                    }
                }
            }
        }
        
        return directories;
    }
    
    public static List<FileItem> scanFiles(File directory, FileItem.FileType filterType) {
        List<FileItem> files = new ArrayList<>();
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return files;
        }
        
        File[] fileList = directory.listFiles();
        if (fileList == null) {
            return files;
        }
        
        for (File file : fileList) {
            if (file.isHidden()) {
                continue;
            }
            
            if (file.isDirectory()) {
                // 递归扫描子目录
                files.addAll(scanFiles(file, filterType));
            } else {
                FileItem item = new FileItem(file);
                if (filterType == null || filterType == FileItem.FileType.OTHER || 
                    item.getFileType() == filterType) {
                    files.add(item);
                }
            }
        }
        
        return files;
    }
    
    public static List<FileItem> scanAllFiles(List<File> storageDirectories, FileItem.FileType filterType) {
        List<FileItem> allFiles = new ArrayList<>();
        for (File directory : storageDirectories) {
            allFiles.addAll(scanFiles(directory, filterType));
        }
        return allFiles;
    }
    
    public static StorageInfo getStorageInfo(File directory) {
        if (directory == null || !directory.exists()) {
            return new StorageInfo(0, 0);
        }
        
        try {
            StatFs statFs = new StatFs(directory.getAbsolutePath());
            long totalBytes;
            long availableBytes;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                totalBytes = statFs.getTotalBytes();
                availableBytes = statFs.getAvailableBytes();
            } else {
                long blockSize = statFs.getBlockSize();
                long totalBlocks = statFs.getBlockCount();
                long availableBlocks = statFs.getAvailableBlocks();
                totalBytes = totalBlocks * blockSize;
                availableBytes = availableBlocks * blockSize;
            }
            
            return new StorageInfo(totalBytes, totalBytes - availableBytes);
        } catch (Exception e) {
            return new StorageInfo(0, 0);
        }
    }
    
    public static class StorageInfo {
        private final long totalBytes;
        private final long usedBytes;
        
        public StorageInfo(long totalBytes, long usedBytes) {
            this.totalBytes = totalBytes;
            this.usedBytes = usedBytes;
        }
        
        public long getTotalBytes() {
            return totalBytes;
        }
        
        public long getUsedBytes() {
            return usedBytes;
        }
        
        public long getFreeBytes() {
            return totalBytes - usedBytes;
        }
        
        public String getFormattedTotal() {
            return formatBytes(totalBytes);
        }
        
        public String getFormattedUsed() {
            return formatBytes(usedBytes);
        }
        
        public String getFormattedFree() {
            return formatBytes(getFreeBytes());
        }
        
        private String formatBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.2f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
}


```

## src/main/java/androidx/media3/demo/media/HomeFragment.java

<!-- 大小: 25,775 bytes -->

```java
package androidx.media3.demo.media;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    
    private RecyclerView recyclerView;
    private TextView emptyView;
    private Button sortTimeButton;
    private Button sortSizeButton;
    private Button viewToggleButton;
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
    private FileItem.FileType currentFilter = FileItem.FileType.VIDEO;
    private SortType currentSort = SortType.TIME;
    private boolean timeSortDescending = true; // 默认从新到旧（降序）
    private boolean sizeSortDescending = true; // 默认从大到小（降序）
    private boolean isGridView = false; // 默认列表视图
    private ExecutorService deleteExecutor;
    private Handler mainHandler;
    private PendingDeleteManager pendingDeleteManager;

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
                // 传递所有视频文件列表，而不是当前筛选后的列表
                intent.putExtra("file_list", getAllVideoFileList());
                // 找到当前视频在所有视频列表中的索引
                int index = findVideoIndexInAllList(currentPath);
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
        
        setupSortButtons();
        setupViewToggleButton();
        setupSelectionButtons();
        
        // 初始化删除线程池和主线程Handler
        deleteExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化待删除管理器并设置监听器
        pendingDeleteManager = PendingDeleteManager.getInstance(getContext());
        pendingDeleteManager.setOnFileDeletedListener(new PendingDeleteManager.OnFileDeletedListener() {
            @Override
            public void onFileDeleted(String filePath) {
                // 单个文件删除后刷新列表
                Log.d(TAG, "onFileDeleted: " + filePath);
                refreshFiles();
            }
            
            @Override
            public void onFilesDeleted(int count) {
                // 批量删除后刷新列表
                Log.d(TAG, "onFilesDeleted: " + count);
                refreshFiles();
                Toast.makeText(getContext(), 
                    getString(R.string.delete_success) + " (" + count + ")", 
                    Toast.LENGTH_SHORT).show();
            }
        });

        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadFiles();
        }

        return view;
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



```

## src/main/java/androidx/media3/demo/media/MainActivity.java

<!-- 大小: 2,730 bytes -->

```java
package androidx.media3.demo.media;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText(R.string.tab_home);
            } else {
                tab.setText(R.string.tab_more);
            }
        }).attach();

        // 请求权限
        if (!PermissionHelper.hasMediaPermissions(this)) {
            PermissionHelper.requestMediaPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            // 权限授予后刷新 Fragment
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
            if (fragment instanceof HomeFragment) {
                ((HomeFragment) fragment).refreshFiles();
            } else if (fragment instanceof MoreFragment) {
                ((MoreFragment) fragment).refreshFiles();
            }
        }
    }

    private static class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                return new HomeFragment();
            } else {
                return new MoreFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}


```

## src/main/java/androidx/media3/demo/media/MoreFragment.java

<!-- 大小: 11,971 bytes -->

```java
package androidx.media3.demo.media;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MoreFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout storageInfoLayout;
    private TextView pathTextView;
    private View backButton;
    private View settingsButton;
    private FileBrowserAdapter adapter;
    private File currentDirectory;
    private List<File> directoryHistory = new ArrayList<>();
    private List<File> storageRoots = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_more, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view);
        storageInfoLayout = view.findViewById(R.id.storage_info_layout);
        pathTextView = view.findViewById(R.id.path_text);
        backButton = view.findViewById(R.id.back_button);
        settingsButton = view.findViewById(R.id.settings_button);

        adapter = new FileBrowserAdapter(new ArrayList<>(), item -> {
            if (item.isDirectory()) {
                navigateToDirectory(item.getFile());
            } else {
                // 可以在这里添加文件点击处理，比如打开文件
                Toast.makeText(getContext(), "文件: " + item.getName(), Toast.LENGTH_SHORT).show();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // 设置返回按钮点击事件
        if (backButton != null) {
            backButton.setOnClickListener(v -> navigateBack());
        }

        // 设置按钮点击事件
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), SettingsActivity.class);
                startActivity(intent);
            });
        }

        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadStorageInfo();
            loadRootDirectories();
        } else {
            showEmptyMessage(getString(R.string.permission_required));
        }

        return view;
    }

    public void refreshFiles() {
        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadStorageInfo();
            if (currentDirectory != null) {
                navigateToDirectory(currentDirectory);
            } else {
                loadRootDirectories();
            }
        }
    }

    private void loadStorageInfo() {
        new Thread(() -> {
            storageRoots = FileUtils.getStorageDirectories(getContext());
            List<FileUtils.StorageInfo> storageInfos = new ArrayList<>();
            
            for (File directory : storageRoots) {
                storageInfos.add(FileUtils.getStorageInfo(directory));
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                storageInfoLayout.removeAllViews();
                
                for (int i = 0; i < storageRoots.size(); i++) {
                    File directory = storageRoots.get(i);
                    FileUtils.StorageInfo info = storageInfos.get(i);
                    
                    String title = getStorageTitle(directory);
                    View infoView = createStorageInfoView(title, info, directory);
                    storageInfoLayout.addView(infoView);
                }
            });
        }).start();
    }

    private String getStorageTitle(File directory) {
        // 判断是内部存储还是外部存储
        File dataDir = Environment.getDataDirectory();
        File externalStorage = Environment.getExternalStorageDirectory();
        
        if (directory.equals(dataDir)) {
            return getString(R.string.internal_storage);
        } else if (directory.equals(externalStorage)) {
            return getString(R.string.external_storage);
        } else {
            // 可能是U盘或其他外置存储
            String path = directory.getAbsolutePath();
            if (path.contains("/storage/")) {
                // 提取存储设备名称
                String[] parts = path.split("/");
                if (parts.length > 2) {
                    return "外置存储: " + parts[parts.length - 1];
                }
            }
            return "存储设备: " + directory.getName();
        }
    }

    private View createStorageInfoView(String title, FileUtils.StorageInfo info, File directory) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        layout.setPadding(16, 16, 16, 16);
        
        // 设置点击事件，点击存储信息可以进入该存储根目录
        layout.setOnClickListener(v -> {
            navigateToDirectory(directory);
        });
        layout.setClickable(true);
        layout.setFocusable(true);
        
        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 8);
        layout.addView(titleView);
        
        TextView pathView = new TextView(getContext());
        pathView.setText("路径: " + directory.getAbsolutePath());
        pathView.setTextSize(12);
        pathView.setTextColor(0xFF666666);
        pathView.setPadding(0, 0, 0, 8);
        layout.addView(pathView);
        
        TextView totalView = new TextView(getContext());
        totalView.setText(getString(R.string.total_space) + ": " + info.getFormattedTotal());
        totalView.setTextSize(14);
        totalView.setPadding(0, 0, 0, 4);
        layout.addView(totalView);
        
        TextView usedView = new TextView(getContext());
        usedView.setText(getString(R.string.used_space) + ": " + info.getFormattedUsed());
        usedView.setTextSize(14);
        usedView.setPadding(0, 0, 0, 4);
        layout.addView(usedView);
        
        TextView freeView = new TextView(getContext());
        freeView.setText(getString(R.string.free_space) + ": " + info.getFormattedFree());
        freeView.setTextSize(14);
        layout.addView(freeView);
        
        // 添加边距
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        layout.setLayoutParams(params);
        
        return layout;
    }

    private void loadRootDirectories() {
        new Thread(() -> {
            storageRoots = FileUtils.getStorageDirectories(getContext());
            List<FileItem> items = new ArrayList<>();
            
            for (File directory : storageRoots) {
                items.add(new FileItem(directory));
            }
            
            // 按名称排序
            Collections.sort(items, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            
            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.updateFiles(items);
                currentDirectory = null;
                directoryHistory.clear();
                updatePathText();
            });
        }).start();
    }

    private void navigateToDirectory(File directory) {
        navigateToDirectory(directory, true);
    }
    
    private void navigateToDirectory(File directory, boolean addToHistory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            Toast.makeText(getContext(), "无法访问该目录", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 添加到历史记录（只有在向前导航时才添加）
        if (addToHistory && currentDirectory != null && !currentDirectory.equals(directory)) {
            directoryHistory.add(currentDirectory);
        }
        
        currentDirectory = directory;
        
        new Thread(() -> {
            List<FileItem> items = new ArrayList<>();
            File[] files = directory.listFiles();
            
            if (files != null) {
                for (File file : files) {
                    if (!file.isHidden()) {
                        items.add(new FileItem(file));
                    }
                }
                
                // 排序：文件夹在前，然后按名称排序
                Collections.sort(items, new Comparator<FileItem>() {
                    @Override
                    public int compare(FileItem a, FileItem b) {
                        if (a.isDirectory() && !b.isDirectory()) {
                            return -1;
                        } else if (!a.isDirectory() && b.isDirectory()) {
                            return 1;
                        } else {
                            return a.getName().compareToIgnoreCase(b.getName());
                        }
                    }
                });
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.updateFiles(items);
                updatePathText();
                
                if (items.isEmpty()) {
                    showEmptyMessage("该目录为空");
                }
            });
        }).start();
    }

    private void navigateBack() {
        if (!directoryHistory.isEmpty()) {
            File previousDirectory = directoryHistory.remove(directoryHistory.size() - 1);
            // 不添加到历史记录，因为这是返回操作
            navigateToDirectory(previousDirectory, false);
        } else if (currentDirectory != null) {
            // 返回根目录
            currentDirectory = null;
            directoryHistory.clear();
            loadRootDirectories();
        }
    }

    private void updatePathText() {
        if (pathTextView != null) {
            if (currentDirectory != null) {
                pathTextView.setText("当前路径: " + currentDirectory.getAbsolutePath());
                pathTextView.setVisibility(View.VISIBLE);
            } else {
                pathTextView.setText("存储根目录");
                pathTextView.setVisibility(View.VISIBLE);
            }
        }
        
        // 更新返回按钮显示状态
        if (backButton != null) {
            if (currentDirectory != null && !directoryHistory.isEmpty()) {
                backButton.setVisibility(View.VISIBLE);
            } else {
                backButton.setVisibility(View.GONE);
            }
        }
    }

    private void showEmptyMessage(String message) {
        // 可以在这里添加空状态显示
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
}


```

## src/main/java/androidx/media3/demo/media/PendingDeleteManager.java

<!-- 大小: 8,508 bytes -->

```java
package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 管理待删除的视频文件
 */
public class PendingDeleteManager {
    private static final String TAG = "PendingDeleteManager";
    private static final String PREFS_NAME = "pending_deletes";
    private static final String KEY_PENDING_FILES = "pending_delete_files";
    private static final String KEY_MAX_COUNT = "max_pending_count";
    private static final int DEFAULT_MAX_COUNT = 5;
    
    private static PendingDeleteManager instance;
    private SharedPreferences prefs;
    private Context context;
    private ExecutorService deleteExecutor;
    private Handler mainHandler;
    private OnFileDeletedListener onFileDeletedListener;
    
    /**
     * 文件删除监听器
     */
    public interface OnFileDeletedListener {
        void onFileDeleted(String filePath);
        void onFilesDeleted(int count);
    }
    
    private PendingDeleteManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.deleteExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized PendingDeleteManager getInstance(Context context) {
        if (instance == null) {
            instance = new PendingDeleteManager(context);
        }
        return instance;
    }
    
    /**
     * 设置文件删除监听器
     */
    public void setOnFileDeletedListener(OnFileDeletedListener listener) {
        this.onFileDeletedListener = listener;
    }
    
    /**
     * 检查文件是否在待删除列表中
     */
    public boolean isPendingDelete(String filePath) {
        Set<String> pendingFiles = getPendingFiles();
        return pendingFiles.contains(filePath);
    }
    
    /**
     * 添加文件到待删除列表
     * 如果超过最大数量，会自动删除最早添加的文件
     */
    public void addPendingDelete(String filePath) {
        LinkedHashSet<String> pendingFiles = new LinkedHashSet<>(getPendingFiles());
        
        if (!pendingFiles.contains(filePath)) {
            pendingFiles.add(filePath);
            
            // 检查是否超过最大数量
            int maxCount = getMaxPendingCount();
            if (pendingFiles.size() > maxCount) {
                // 删除最早添加的文件（异步）
                String firstFile = pendingFiles.iterator().next();
                pendingFiles.remove(firstFile);
                deleteFileAsync(firstFile);
            }
            
            savePendingFiles(pendingFiles);
        }
    }
    
    /**
     * 从待删除列表中移除文件
     */
    public void removePendingDelete(String filePath) {
        Set<String> pendingFiles = getPendingFiles();
        if (pendingFiles.remove(filePath)) {
            savePendingFiles(pendingFiles);
        }
    }
    
    /**
     * 切换待删除状态
     */
    public boolean togglePendingDelete(String filePath) {
        if (isPendingDelete(filePath)) {
            removePendingDelete(filePath);
            return false;
        } else {
            addPendingDelete(filePath);
            return true;
        }
    }
    
    /**
     * 获取所有待删除的文件
     */
    public List<String> getPendingDeleteList() {
        return new ArrayList<>(getPendingFiles());
    }
    
    /**
     * 获取待删除文件数量
     */
    public int getPendingCount() {
        return getPendingFiles().size();
    }
    
    /**
     * 删除所有待删除标记的文件
     */
    public void deleteAllPending() {
        List<String> pendingFiles = getPendingDeleteList();
        if (pendingFiles.isEmpty()) {
            return;
        }
        
        final int totalCount = pendingFiles.size();
        
        // 异步删除所有文件
        deleteExecutor.execute(() -> {
            int successCount = 0;
            
            for (String filePath : pendingFiles) {
                if (deleteFile(filePath)) {
                    successCount++;
                }
            }
            
            // 清空待删除列表
            clearAllPending();
            
            // 在主线程回调
            final int finalSuccessCount = successCount;
            mainHandler.post(() -> {
                if (onFileDeletedListener != null) {
                    onFileDeletedListener.onFilesDeleted(finalSuccessCount);
                }
            });
        });
    }
    
    /**
     * 清空待删除列表（不删除文件）
     */
    public void clearAllPending() {
        prefs.edit().remove(KEY_PENDING_FILES).apply();
    }
    
    /**
     * 获取最大待删除数量设置
     */
    public int getMaxPendingCount() {
        return prefs.getInt(KEY_MAX_COUNT, DEFAULT_MAX_COUNT);
    }
    
    /**
     * 设置最大待删除数量
     */
    public void setMaxPendingCount(int count) {
        if (count < 1) {
            count = 1;
        }
        prefs.edit().putInt(KEY_MAX_COUNT, count).apply();
        
        // 检查当前待删除列表是否超过新的最大值
        LinkedHashSet<String> pendingFiles = new LinkedHashSet<>(getPendingFiles());
        while (pendingFiles.size() > count) {
            // 删除最早添加的文件（异步）
            String firstFile = pendingFiles.iterator().next();
            pendingFiles.remove(firstFile);
            deleteFileAsync(firstFile);
        }
        savePendingFiles(pendingFiles);
    }
    
    /**
     * 获取待删除文件集合
     */
    private Set<String> getPendingFiles() {
        return prefs.getStringSet(KEY_PENDING_FILES, new LinkedHashSet<>());
    }
    
    /**
     * 保存待删除文件集合
     */
    private void savePendingFiles(Set<String> files) {
        // SharedPreferences 要求使用新的 Set 对象
        prefs.edit().putStringSet(KEY_PENDING_FILES, new LinkedHashSet<>(files)).apply();
    }
    
    /**
     * 异步删除单个文件
     */
    private void deleteFileAsync(String filePath) {
        deleteExecutor.execute(() -> {
            boolean deleted = deleteFile(filePath);
            if (deleted) {
                // 在主线程回调
                mainHandler.post(() -> {
                    if (onFileDeletedListener != null) {
                        onFileDeletedListener.onFileDeleted(filePath);
                    }
                });
            }
        });
    }
    
    /**
     * 删除单个文件（同步方法，在后台线程调用）
     */
    private boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Log.w(TAG, "deleteFile: 文件不存在 - " + filePath);
            return false;
        }
        
        boolean deleted = false;
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 尝试使用 MediaStore API
                // 这里简化处理，直接使用 File.delete()
                deleted = file.delete();
            } else {
                // Android 9 及以下
                deleted = file.delete();
            }
            
            if (deleted) {
                Log.d(TAG, "deleteFile: 删除成功 - " + file.getName());
            } else {
                Log.e(TAG, "deleteFile: 删除失败 - " + file.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, "deleteFile: 删除异常 - " + file.getName(), e);
        }
        
        return deleted;
    }
    
    /**
     * 清理资源
     */
    public void shutdown() {
        if (deleteExecutor != null && !deleteExecutor.isShutdown()) {
            deleteExecutor.shutdown();
        }
    }
}

```

## src/main/java/androidx/media3/demo/media/PermissionHelper.java

<!-- 大小: 3,544 bytes -->

```java
package androidx.media3.demo.media;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {
    
    public static final int PERMISSION_REQUEST_CODE = 100;
    
    public static boolean hasStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用分区存储，不需要权限
            return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 使用分区存储
            return true;
        } else {
            // Android 9 及以下需要 READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    public static void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 不需要权限
            return;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 不需要权限
            return;
        } else {
            // Android 9 及以下请求权限
            List<String> permissions = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (!permissions.isEmpty()) {
                ActivityCompat.requestPermissions(activity, 
                    permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    public static boolean hasMediaPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要细粒度权限
            return ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
        return hasStoragePermission(activity);
    }
    
    public static void requestMediaPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<String> permissions = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(activity, 
                Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (!permissions.isEmpty()) {
                ActivityCompat.requestPermissions(activity, 
                    permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        } else {
            requestStoragePermission(activity);
        }
    }
}


```

## src/main/java/androidx/media3/demo/media/PermissionManager.java

<!-- 大小: 3,621 bytes -->

```java
package androidx.media3.demo.media;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

/**
 * 权限管理器 - 处理 MANAGE_EXTERNAL_STORAGE 权限
 */
public class PermissionManager {
    
    private static final String TAG = "PermissionManager";
    public static final int REQUEST_MANAGE_STORAGE = 1002;
    
    /**
     * 检查是否有管理所有文件的权限
     */
    public static boolean hasManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasPermission = Environment.isExternalStorageManager();
            Log.d(TAG, "hasManageStoragePermission: " + hasPermission);
            return hasPermission;
        }
        // Android 10 及以下不需要此权限
        return true;
    }
    
    /**
     * 请求管理所有文件权限
     */
    public static void requestManageStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showPermissionDialog(activity);
            }
        }
    }
    
    /**
     * 显示权限说明对话框
     */
    private static void showPermissionDialog(Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("需要文件管理权限")
            .setMessage("为了删除文件，需要授予\"管理所有文件\"权限。\n\n" +
                "点击\"前往设置\"后，请选择\"允许管理所有文件\"。")
            .setPositiveButton("前往设置", (dialog, which) -> {
                openManageStorageSettings(activity);
            })
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show();
    }
    
    /**
     * 打开管理存储权限设置页面
     */
    public static void openManageStorageSettings(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                Log.d(TAG, "openManageStorageSettings: 打开设置页面");
            } catch (Exception e) {
                Log.e(TAG, "openManageStorageSettings: 无法打开设置页面", e);
                // 如果失败，打开通用的应用设置页面
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                } catch (Exception ex) {
                    Log.e(TAG, "openManageStorageSettings: 也无法打开应用设置", ex);
                }
            }
        }
    }
    
    /**
     * 检查并请求权限（如果需要）
     * @return true 如果已有权限，false 如果需要请求
     */
    public static boolean checkAndRequestIfNeeded(Activity activity) {
        if (!hasManageStoragePermission()) {
            requestManageStoragePermission(activity);
            return false;
        }
        return true;
    }
}

```

## src/main/java/androidx/media3/demo/media/PlayerActivity.java

<!-- 大小: 9,908 bytes -->

```java
package androidx.media3.demo.media;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";
    
    private PlayerView playerView;
    private ImageButton favoriteButton;
    private ImageButton pendingDeleteButton;
    private ExoPlayer player;
    private ArrayList<String> fileList;
    private int currentIndex;
    private String currentFilePath;
    private FavoriteManager favoriteManager;
    private PendingDeleteManager pendingDeleteManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        playerView = findViewById(R.id.player_view);
        favoriteButton = findViewById(R.id.favorite_button);
        pendingDeleteButton = findViewById(R.id.pending_delete_button);
        
        favoriteManager = FavoriteManager.getInstance(this);
        pendingDeleteManager = PendingDeleteManager.getInstance(this);
        
        Intent intent = getIntent();
        currentFilePath = intent.getStringExtra("file_path");
        fileList = intent.getStringArrayListExtra("file_list");
        currentIndex = intent.getIntExtra("current_index", 0);

        if (fileList == null) {
            fileList = new ArrayList<>();
            if (currentFilePath != null) {
                fileList.add(currentFilePath);
            }
        }

        initializePlayer();
        updateFavoriteButton();
        updatePendingDeleteButton();

        favoriteButton.setOnClickListener(v -> toggleFavorite());
        pendingDeleteButton.setOnClickListener(v -> togglePendingDelete());
    }
    
    private void toggleFavorite() {
        if (currentFilePath == null) {
            return;
        }
        
        boolean isFavorited = favoriteManager.toggleFavorite(currentFilePath);
        updateFavoriteButton();
        
        String message = isFavorited 
            ? getString(R.string.favorited) 
            : getString(R.string.unfavorited);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void togglePendingDelete() {
        if (currentFilePath == null) {
            return;
        }
        
        boolean isPending = pendingDeleteManager.togglePendingDelete(currentFilePath);
        updatePendingDeleteButton();
        
        String message = isPending 
            ? getString(R.string.marked_for_deletion) 
            : getString(R.string.unmarked_for_deletion);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void updateFavoriteButton() {
        if (currentFilePath == null) {
            return;
        }
        
        boolean isFavorited = favoriteManager.isFavorite(currentFilePath);
        if (isFavorited) {
            // 实心黄色星星
            favoriteButton.setImageResource(android.R.drawable.btn_star_big_on);
            favoriteButton.setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN);
        } else {
            // 空心星星
            favoriteButton.setImageResource(android.R.drawable.btn_star_big_off);
            favoriteButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
    }
    
    private void updatePendingDeleteButton() {
        if (currentFilePath == null) {
            return;
        }
        
        boolean isPending = pendingDeleteManager.isPendingDelete(currentFilePath);
        if (isPending) {
            // 红色删除图标
            pendingDeleteButton.setColorFilter(Color.parseColor("#FF0000"), PorterDuff.Mode.SRC_IN);
        } else {
            // 白色删除图标
            pendingDeleteButton.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }
    }

    private void initializePlayer() {
        if (currentFilePath == null || fileList.isEmpty()) {
            finish();
            return;
        }

        // 如果播放器已存在，先释放
        if (player != null) {
            player.release();
            player = null;
        }

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // 将所有视频文件转换为 MediaItem 列表
        List<MediaItem> mediaItems = new ArrayList<>();
        for (String filePath : fileList) {
            File file = new File(filePath);
            if (file.exists()) {
                mediaItems.add(MediaItem.fromUri(Uri.fromFile(file)));
            }
        }

        if (mediaItems.isEmpty()) {
            finish();
            return;
        }

        // 使用 setMediaItems 设置播放列表，这样"下一个"按钮才会可用
        // 从当前索引开始播放
        player.setMediaItems(mediaItems, currentIndex, 0);
        player.prepare();
        player.play();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                // 播放错误时播放下一个
                playNext();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                // 当播放结束时，自动播放下一个
                if (playbackState == Player.STATE_ENDED) {
                    // 播放结束，切换到下一个视频
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem();
                    } else {
                        // 没有下一个视频，关闭播放器
                        finish();
                    }
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                // 当切换到新的视频时，更新当前索引和文件路径
                if (player.getCurrentMediaItemIndex() >= 0 && 
                    player.getCurrentMediaItemIndex() < fileList.size()) {
                    currentIndex = player.getCurrentMediaItemIndex();
                    currentFilePath = fileList.get(currentIndex);
                    updateFavoriteButton();
                    updatePendingDeleteButton();
                }
            }
        });
    }

    private void playNext() {
        // 如果使用播放列表，直接使用 ExoPlayer 的导航功能
        if (player != null && player.getMediaItemCount() > 0) {
            // 从列表中移除当前文件
            if (currentIndex >= 0 && currentIndex < fileList.size()) {
                fileList.remove(currentIndex);
            }
            
            // 如果列表为空，关闭播放器
            if (fileList.isEmpty()) {
                finish();
                return;
            }
            
            // 重新构建播放列表
            List<MediaItem> mediaItems = new ArrayList<>();
            for (String filePath : fileList) {
                File file = new File(filePath);
                if (file.exists()) {
                    mediaItems.add(MediaItem.fromUri(Uri.fromFile(file)));
                }
            }
            
            if (mediaItems.isEmpty()) {
                finish();
                return;
            }
            
            // 调整索引
            if (currentIndex >= fileList.size()) {
                currentIndex = fileList.size() - 1;
            }
            if (currentIndex < 0) {
                currentIndex = 0;
            }
            
            // 更新播放列表
            player.setMediaItems(mediaItems, currentIndex, 0);
            player.prepare();
            player.play();
            
            // 更新当前文件路径
            if (currentIndex < fileList.size()) {
                currentFilePath = fileList.get(currentIndex);
            }
        } else {
            // 备用方案：重新初始化播放器
            if (currentIndex >= fileList.size()) {
                finish();
                return;
            }

            // 从当前索引开始查找存在的视频文件
            while (currentIndex < fileList.size()) {
                String nextPath = fileList.get(currentIndex);
                File nextFile = new File(nextPath);
                if (nextFile.exists()) {
                    currentFilePath = nextPath;
                    initializePlayer();
                    return;
                }
                // 文件不存在，继续查找下一个
                currentIndex++;
            }

            // 没有找到存在的视频文件，关闭播放器
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) {
            playerView.onResume();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            playerView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}

```

## src/main/java/androidx/media3/demo/media/SettingsActivity.java

<!-- 大小: 4,371 bytes -->

```java
package androidx.media3.demo.media;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private PendingDeleteManager pendingDeleteManager;
    private TextView pendingCountText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        pendingDeleteManager = PendingDeleteManager.getInstance(this);
        
        // 设置监听器
        pendingDeleteManager.setOnFileDeletedListener(new PendingDeleteManager.OnFileDeletedListener() {
            @Override
            public void onFileDeleted(String filePath) {
                // 自动删除时显示提示
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, 
                        "已自动删除文件", 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onFilesDeleted(int count) {
                // 批量删除时显示提示
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, 
                        "已删除 " + count + " 个文件", 
                        Toast.LENGTH_SHORT).show();
                });
            }
        });

        // 返回按钮
        View backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // 设置版本信息
        TextView versionText = findViewById(R.id.version_text);
        TextView packageNameText = findViewById(R.id.package_name_text);
        TextView buildTimeText = findViewById(R.id.build_time_text);
        TextView deviceInfoText = findViewById(R.id.device_info_text);

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionText.setText(packageInfo.versionName + " (" + packageInfo.versionCode + ")");
            packageNameText.setText(getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("未知");
            packageNameText.setText("未知");
        }

        // 设置编译时间（从 BuildConfig 获取或使用当前时间）
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        buildTimeText.setText(sdf.format(new Date()));

        // 设置设备信息
        String deviceInfo = Build.BRAND + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")";
        deviceInfoText.setText(deviceInfo);

        // 待删除数量设置
        pendingCountText = findViewById(R.id.pending_count_text);
        Button decreaseButton = findViewById(R.id.decrease_button);
        Button increaseButton = findViewById(R.id.increase_button);

        updatePendingCountDisplay();

        decreaseButton.setOnClickListener(v -> {
            int current = pendingDeleteManager.getMaxPendingCount();
            if (current > 1) {
                pendingDeleteManager.setMaxPendingCount(current - 1);
                updatePendingCountDisplay();
            }
        });

        increaseButton.setOnClickListener(v -> {
            int current = pendingDeleteManager.getMaxPendingCount();
            if (current < 99) {
                pendingDeleteManager.setMaxPendingCount(current + 1);
                updatePendingCountDisplay();
            }
        });
    }

    private void updatePendingCountDisplay() {
        int count = pendingDeleteManager.getMaxPendingCount();
        pendingCountText.setText(String.valueOf(count));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除监听器
        if (pendingDeleteManager != null) {
            pendingDeleteManager.setOnFileDeletedListener(null);
        }
    }
}

```

## src/main/res/layout/activity_main.xml

<!-- 大小: 957 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/view_pager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tab_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="?attr/colorPrimary"
        app:tabMode="fixed"
        app:tabGravity="fill"
        app:tabIndicatorHeight="0dp"
        app:tabTextColor="@android:color/black"
        app:tabSelectedTextColor="@android:color/black" />

</LinearLayout>


```

## src/main/res/layout/activity_player.xml

<!-- 大小: 1,564 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.media3.ui.PlayerView
        android:id="@+id/player_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:use_controller="true" />

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="left|top"
        android:layout_margin="16dp"
        android:orientation="horizontal">

        <ImageButton
            android:id="@+id/favorite_button"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:src="@android:drawable/btn_star_big_off"
            android:background="@android:color/transparent"
            android:scaleType="fitCenter"
            android:contentDescription="@string/favorite" />

        <ImageButton
            android:id="@+id/pending_delete_button"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:layout_marginStart="8dp"
            android:src="@android:drawable/ic_menu_delete"
            android:background="@android:color/transparent"
            android:scaleType="fitCenter"
            android:contentDescription="@string/pending_delete" />

    </LinearLayout>

</FrameLayout>


```

## src/main/res/layout/activity_settings.xml

<!-- 大小: 15,499 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    android:background="#F5F5F5">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="24dp">

        <ImageButton
            android:id="@+id/back_button"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@android:drawable/ic_menu_revert"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="返回" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="关于应用"
            android:textSize="20sp"
            android:textStyle="bold"
            android:gravity="center" />

        <View
            android:layout_width="48dp"
            android:layout_height="48dp" />

    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- 应用信息卡片 -->
            <androidx.cardview.widget.CardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:elevation="4dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="应用信息"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:layout_marginBottom="12dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginBottom="8dp">

                        <TextView
                            android:layout_width="100dp"
                            android:layout_height="wrap_content"
                            android:text="应用名称："
                            android:textColor="#666666" />

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/app_name" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginBottom="8dp">

                        <TextView
                            android:layout_width="100dp"
                            android:layout_height="wrap_content"
                            android:text="版本号："
                            android:textColor="#666666" />

                        <TextView
                            android:id="@+id/version_text"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="1.0.0" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="100dp"
                            android:layout_height="wrap_content"
                            android:text="包名："
                            android:textColor="#666666" />

                        <TextView
                            android:id="@+id/package_name_text"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="com.example.app" />
                    </LinearLayout>

                </LinearLayout>

            </androidx.cardview.widget.CardView>

            <!-- 开发者信息卡片 -->
            <androidx.cardview.widget.CardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:elevation="4dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="开发者信息"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:layout_marginBottom="12dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginBottom="8dp">

                        <TextView
                            android:layout_width="100dp"
                            android:layout_height="wrap_content"
                            android:text="开发者："
                            android:textColor="#666666" />

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/developer_name" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="100dp"
                            android:layout_height="wrap_content"
                            android:text="联系方式："
                            android:textColor="#666666" />

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="@string/developer_contact" />
                    </LinearLayout>

                </LinearLayout>

            </androidx.cardview.widget.CardView>

            <!-- 待删除设置卡片 -->
            <androidx.cardview.widget.CardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:elevation="4dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="待删除设置"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:layout_marginBottom="12dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="最大待删除数量："
                            android:textColor="#666666" />

                        <Button
                            android:id="@+id/decrease_button"
                            android:layout_width="40dp"
                            android:layout_height="40dp"
                            android:text="-"
                            android:textSize="20sp"
                            android:padding="0dp"
                            style="@android:style/Widget.Material.Button.Borderless" />

                        <TextView
                            android:id="@+id/pending_count_text"
                            android:layout_width="60dp"
                            android:layout_height="wrap_content"
                            android:text="5"
                            android:textSize="18sp"
                            android:textStyle="bold"
                            android:gravity="center" />

                        <Button
                            android:id="@+id/increase_button"
                            android:layout_width="40dp"
                            android:layout_height="40dp"
                            android:text="+"
                            android:textSize="20sp"
                            android:padding="0dp"
                            style="@android:style/Widget.Material.Button.Borderless" />

                    </LinearLayout>

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="超过此数量后，将自动删除最早标记的视频"
                        android:textSize="12sp"
                        android:textColor="#999999"
                        android:layout_marginTop="8dp" />

                </LinearLayout>

            </androidx.cardview.widget.CardView>

            <!-- 技术信息卡片 -->
            <androidx.cardview.widget.CardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:elevation="4dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="技术信息"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:layout_marginBottom="12dp" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginBottom="8dp">

                        <TextView
                            android:layout_width="100dp"
                            android:layout_height="wrap_content"
                            android:text="基于："
                            android:textColor="#666666" />

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="AndroidX Media3" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:layout_marginBottom="8dp">

                        <TextView
                            android:layout_width="100dp"
                            android:layout_height="wrap_content"
                            android:text="Build Time："
                            android:textColor="#666666" />

                        <TextView
                            android:id="@+id/build_time_text"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="2024-12-21" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:layout_width="100dp"
                            android:layout_height="wrap_content"
                            android:text="设备信息："
                            android:textColor="#666666" />

                        <TextView
                            android:id="@+id/device_info_text"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="" />
                    </LinearLayout>

                </LinearLayout>

            </androidx.cardview.widget.CardView>

            <!-- 版权信息 -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/copyright_text"
                android:textSize="12sp"
                android:textColor="#999999"
                android:gravity="center"
                android:layout_marginTop="24dp" />

        </LinearLayout>

    </ScrollView>

</LinearLayout>

```

## src/main/res/layout/fragment_home.xml

<!-- 大小: 5,796 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <LinearLayout
        android:id="@+id/toolbar_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        android:layout_marginTop="16dp"
        android:gravity="end|center_vertical">

        <Button
            android:id="@+id/sort_time_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="8dp"
            android:text="@string/sort_by_time"
            android:drawableEnd="@android:drawable/arrow_down_float"
            android:drawablePadding="4dp"
            style="@android:style/Widget.Material.Button.Borderless" />

        <Button
            android:id="@+id/sort_size_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="8dp"
            android:text="@string/sort_by_size"
            android:drawableEnd="@android:drawable/arrow_down_float"
            android:drawablePadding="4dp"
            style="@android:style/Widget.Material.Button.Borderless" />

        <Button
            android:id="@+id/view_toggle_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:drawablePadding="4dp"
            android:text="@string/grid_view"
            style="@android:style/Widget.Material.Button.Borderless" />

    </LinearLayout>

    <LinearLayout
        android:id="@+id/selection_toolbar_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        android:layout_marginTop="16dp"
        android:gravity="center_vertical"
        android:visibility="gone"
        android:background="@android:color/white">

        <Button
            android:id="@+id/select_all_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="4dp"
            android:text="@string/select_all"
            android:textSize="12sp"
            android:minWidth="0dp"
            android:paddingStart="8dp"
            android:paddingEnd="8dp"
            style="@android:style/Widget.Material.Button.Borderless" />

        <Button
            android:id="@+id/select_favorites_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="4dp"
            android:text="@string/select_all_favorites"
            android:textSize="12sp"
            android:minWidth="0dp"
            android:paddingStart="8dp"
            android:paddingEnd="8dp"
            style="@android:style/Widget.Material.Button.Borderless" />

        <Button
            android:id="@+id/select_pending_deletes_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="4dp"
            android:text="@string/select_pending_deletes"
            android:textSize="12sp"
            android:minWidth="0dp"
            android:paddingStart="8dp"
            android:paddingEnd="8dp"
            style="@android:style/Widget.Material.Button.Borderless" />

        <TextView
            android:id="@+id/selected_count_text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/selected_count"
            android:textSize="12sp"
            android:gravity="center" />

        <Button
            android:id="@+id/delete_selected_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="4dp"
            android:text="@string/delete_selected"
            android:textSize="12sp"
            android:minWidth="0dp"
            android:paddingStart="8dp"
            android:paddingEnd="8dp"
            android:textColor="@android:color/holo_red_dark"
            style="@android:style/Widget.Material.Button.Borderless" />

        <Button
            android:id="@+id/cancel_selection_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/cancel_selection"
            android:textSize="12sp"
            android:minWidth="0dp"
            android:paddingStart="8dp"
            android:paddingEnd="8dp"
            style="@android:style/Widget.Material.Button.Borderless" />

    </LinearLayout>

    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recycler_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:padding="8dp" />

        <TextView
            android:id="@+id/empty_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:text="@string/no_files"
            android:visibility="gone" />

    </FrameLayout>

</LinearLayout>


```

## src/main/res/layout/fragment_more.xml

<!-- 大小: 2,638 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/storage_info"
        android:textSize="18sp"
        android:textStyle="bold"
        android:layout_marginBottom="16dp" />

    <LinearLayout
        android:id="@+id/storage_info_layout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:layout_marginBottom="16dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="8dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="文件浏览器"
            android:textSize="18sp"
            android:textStyle="bold" />

        <ImageButton
            android:id="@+id/back_button"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@android:drawable/ic_menu_revert"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="返回上一级"
            android:visibility="gone" />

        <ImageButton
            android:id="@+id/settings_button"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@android:drawable/ic_menu_preferences"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:contentDescription="设置" />

    </LinearLayout>

    <TextView
        android:id="@+id/path_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:textColor="@android:color/darker_gray"
        android:layout_marginBottom="8dp"
        android:maxLines="2"
        android:ellipsize="end" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recycler_view"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>


```

## src/main/res/layout/item_file.xml

<!-- 大小: 2,337 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="12dp"
    android:gravity="center_vertical"
    android:background="?android:attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true">

    <CheckBox
        android:id="@+id/checkbox"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="12dp"
        android:visibility="gone"
        android:clickable="false"
        android:focusable="false" />

    <ImageView
        android:id="@+id/icon"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginEnd="12dp"
        android:src="@android:drawable/ic_menu_gallery" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/file_name"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold"
            android:maxLines="1"
            android:ellipsize="end" />

        <TextView
            android:id="@+id/file_info"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="@android:color/darker_gray"
            android:layout_marginTop="4dp" />

    </LinearLayout>

    <ImageView
        android:id="@+id/favorite_icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@android:drawable/btn_star_big_on"
        android:visibility="gone"
        android:layout_marginStart="8dp" />

    <ImageView
        android:id="@+id/pending_delete_icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@android:drawable/ic_menu_delete"
        android:visibility="gone"
        android:layout_marginStart="8dp" />

</LinearLayout>


```

## src/main/res/values/strings.xml

<!-- 大小: 2,400 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">文件浏览器</string>
    <string name="tab_home">首页</string>
    <string name="tab_more">更多</string>
    <string name="filter_video">视频</string>
    <string name="filter_audio">音乐</string>
    <string name="filter_document">文档</string>
    <string name="filter_all">全部</string>
    <string name="sort_by_time">按时间排序</string>
    <string name="sort_by_size">按大小排序</string>
    <string name="delete">删除</string>
    <string name="delete_confirm">确定要删除这个文件吗？</string>
    <string name="storage_info">存储信息</string>
    <string name="total_space">总空间</string>
    <string name="used_space">已用空间</string>
    <string name="free_space">可用空间</string>
    <string name="internal_storage">内部存储</string>
    <string name="external_storage">外部存储</string>
    <string name="no_files">没有找到文件</string>
    <string name="loading">加载中...</string>
    <string name="permission_required">需要存储权限才能访问文件</string>
    <string name="list_view">列表视图</string>
    <string name="grid_view">网格视图</string>
    <string name="select_all">全选</string>
    <string name="deselect_all">取消全选</string>
    <string name="cancel_selection">取消</string>
    <string name="delete_selected">删除选中</string>
    <string name="selected_count">已选择 %d 项</string>
    <string name="delete_success">删除成功</string>
    <string name="delete_failed">删除失败</string>
    <string name="developer_name">AndroidX Media3 Team</string>
    <string name="developer_contact">15521288734@163.com</string>
    <string name="copyright_text">Copyright © 2024 AndroidX Media3\n基于 Apache License 2.0 开源协议</string>
    <string name="favorite">收藏</string>
    <string name="favorited">已收藏</string>
    <string name="unfavorited">已取消收藏</string>
    <string name="select_all_favorites">选择所有收藏</string>
    <string name="pending_delete">待删除</string>
    <string name="marked_for_deletion">已标记为待删除</string>
    <string name="unmarked_for_deletion">已取消待删除标记</string>
    <string name="select_pending_deletes">选择待删除</string>
</resources>


```

## src/main/res/values/styles.xml

<!-- 大小: 281 bytes -->

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="PlayerTheme" parent="Theme.AppCompat.Light.NoActionBar">
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowContentOverlay">@null</item>
    </style>
</resources>


```
