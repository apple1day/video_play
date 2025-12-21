package androidx.media3.demo.filebrowser;

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

