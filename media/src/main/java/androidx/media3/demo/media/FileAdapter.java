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
import androidx.collection.LruCache;
import androidx.recyclerview.widget.DiffUtil;
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
    private OnItemLongClickListener longClickListener;
    private OnSelectionChangeListener selectionChangeListener;
    private boolean isSelectionMode = false;
    private Set<Integer> selectedPositions = new HashSet<>();
    private FavoriteManager favoriteManager;
    private PendingDeleteManager pendingDeleteManager;
    private WatchedManager watchedManager;
    private Context context;
    
    // 性能优化：添加状态缓存，避免频繁查询SharedPreferences
    private LruCache<String, Boolean> favoriteCache = new LruCache<>(200);
    private LruCache<String, Boolean> watchedCache = new LruCache<>(200);
    private LruCache<String, Boolean> pendingDeleteCache = new LruCache<>(200);

    public interface OnItemClickListener {
        void onItemClick(FileItem item);
    }
    
    public interface OnItemLongClickListener {
        boolean onItemLongClick(FileItem item);
    }
    
    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }
    
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
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
        this.watchedManager = WatchedManager.getInstance(context);
    }

    public void updateFiles(List<FileItem> newFiles) {
        // 使用DiffUtil计算差异，避免全量刷新
        List<FileItem> oldFiles = this.files;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldFiles.size();
            }

            @Override
            public int getNewListSize() {
                return newFiles.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                FileItem oldItem = oldFiles.get(oldItemPosition);
                FileItem newItem = newFiles.get(newItemPosition);
                return oldItem.getFile().getAbsolutePath().equals(newItem.getFile().getAbsolutePath());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FileItem oldItem = oldFiles.get(oldItemPosition);
                FileItem newItem = newFiles.get(newItemPosition);
                // 比较文件的关键属性
                return oldItem.getFile().getAbsolutePath().equals(newItem.getFile().getAbsolutePath()) &&
                       oldItem.getLastModified() == newItem.getLastModified() &&
                       oldItem.getSize() == newItem.getSize();
            }
        });
        
        this.files = newFiles;
        selectedPositions.clear();
        // 清除缓存，确保数据一致性
        clearCache();
        diffResult.dispatchUpdatesTo(this);
    }
    
    /**
     * 清除所有状态缓存
     */
    public void clearCache() {
        favoriteCache.evictAll();
        watchedCache.evictAll();
        pendingDeleteCache.evictAll();
    }
    
    /**
     * 清除特定文件的缓存
     */
    public void clearCacheForFile(String filePath) {
        favoriteCache.remove(filePath);
        watchedCache.remove(filePath);
        pendingDeleteCache.remove(filePath);
    }
    
    /**
     * 追加文件列表（用于分页加载）
     */
    public void appendFiles(List<FileItem> newFiles) {
        if (newFiles == null || newFiles.isEmpty()) {
            return;
        }
        int startPosition = this.files.size();
        this.files.addAll(newFiles);
        notifyItemRangeInserted(startPosition, newFiles.size());
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
    
    /**
     * 选择所有已观看的文件
     */
    public void selectAllWatched() {
        // 确保 watchedManager 已初始化
        if (watchedManager == null && context != null) {
            watchedManager = WatchedManager.getInstance(context);
        }
        
        if (watchedManager == null) {
            return;
        }
        
        selectedPositions.clear();
        for (int i = 0; i < files.size(); i++) {
            FileItem item = files.get(i);
            if (item.getFileType() == FileItem.FileType.VIDEO) {
                String filePath = item.getFile().getAbsolutePath();
                if (watchedManager.isWatched(filePath)) {
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
    
    /**
     * 选择指定位置的项目
     */
    public void selectPositions(List<Integer> positions) {
        selectedPositions.clear();
        selectedPositions.addAll(positions);
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedPositions.size());
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
        watchedManager = WatchedManager.getInstance(context);
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
        private ImageView watchedIcon;
        private ImageView favoriteIcon;
        private ImageView pendingDeleteIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileInfo = itemView.findViewById(R.id.file_info);
            checkBox = itemView.findViewById(R.id.checkbox);
            watchedIcon = itemView.findViewById(R.id.watched_icon);
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
                if (position != RecyclerView.NO_POSITION) {
                    // 如果设置了独立的长按监听器，优先使用它
                    if (longClickListener != null) {
                        return longClickListener.onItemLongClick(files.get(position));
                    }
                    
                    // 否则，长按进入多选模式（仅在非多选模式下）
                    if (!isSelectionMode) {
                        setSelectionMode(true);
                        toggleSelection(position);
                        return true;
                    }
                }
                return false;
            });
        }

        public void bind(FileItem item, int position) {
            fileName.setText(item.getName());
            
            // 优化：移除clearThumbnail调用，让Glide自己管理请求，减少不必要的clear操作
            
            // 设置图标/缩略图
            if (item.isDirectory()) {
                icon.setImageResource(android.R.drawable.ic_dialog_info);
            } else {
                switch (item.getFileType()) {
                    case VIDEO:
                        // 为视频加载缩略图（已优化，带缓存key）
                        ThumbnailLoader.loadThumbnail(context, item.getFile(), icon);
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
            
            // 设置已观看图标（优化：使用缓存）
            if (watchedIcon != null && watchedManager != null) {
                if (item.getFileType() == FileItem.FileType.VIDEO) {
                    String filePath = item.getFile().getAbsolutePath();
                    Boolean isWatched = watchedCache.get(filePath);
                    if (isWatched == null) {
                        isWatched = watchedManager.isWatched(filePath);
                        watchedCache.put(filePath, isWatched);
                    }
                    if (isWatched) {
                        watchedIcon.setVisibility(View.VISIBLE);
                        watchedIcon.setColorFilter(Color.parseColor("#4CAF50"), PorterDuff.Mode.SRC_IN); // 绿色
                    } else {
                        watchedIcon.setVisibility(View.GONE);
                    }
                } else {
                    watchedIcon.setVisibility(View.GONE);
                }
            }
            
            // 设置收藏图标（优化：使用缓存）
            if (favoriteIcon != null && favoriteManager != null) {
                if (item.getFileType() == FileItem.FileType.VIDEO) {
                    String filePath = item.getFile().getAbsolutePath();
                    Boolean isFavorited = favoriteCache.get(filePath);
                    if (isFavorited == null) {
                        isFavorited = favoriteManager.isFavorite(filePath);
                        favoriteCache.put(filePath, isFavorited);
                    }
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
            
            // 设置待删除图标（优化：使用缓存）
            if (pendingDeleteIcon != null && pendingDeleteManager != null) {
                if (item.getFileType() == FileItem.FileType.VIDEO) {
                    String filePath = item.getFile().getAbsolutePath();
                    Boolean isPending = pendingDeleteCache.get(filePath);
                    if (isPending == null) {
                        isPending = pendingDeleteManager.isPendingDelete(filePath);
                        pendingDeleteCache.put(filePath, isPending);
                    }
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

