package androidx.media3.demo.media;

import android.graphics.Color;
import android.graphics.PorterDuff;
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
    private OnItemLongClickListener longClickListener;
    private FavoriteManager favoriteManager;
    private PendingDeleteManager pendingDeleteManager;

    public interface OnItemClickListener {
        void onItemClick(FileItem item);
    }
    
    public interface OnItemLongClickListener {
        boolean onItemLongClick(FileItem item);
    }

    public FileBrowserAdapter(List<FileItem> files, OnItemClickListener listener, 
                             FavoriteManager favoriteManager, PendingDeleteManager pendingDeleteManager) {
        this.files = files;
        this.listener = listener;
        this.favoriteManager = favoriteManager;
        this.pendingDeleteManager = pendingDeleteManager;
    }
    
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void updateFiles(List<FileItem> newFiles) {
        this.files = newFiles;
        notifyDataSetChanged();
    }
    
    public List<FileItem> getFiles() {
        return files;
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
        private ImageView favoriteIcon;
        private ImageView pendingDeleteIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileInfo = itemView.findViewById(R.id.file_info);
            favoriteIcon = itemView.findViewById(R.id.favorite_icon);
            pendingDeleteIcon = itemView.findViewById(R.id.pending_delete_icon);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(files.get(position));
                }
            });
            
            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && longClickListener != null) {
                    return longClickListener.onItemLongClick(files.get(position));
                }
                return false;
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
                
                // 文件夹不显示收藏和待删除标记
                favoriteIcon.setVisibility(View.GONE);
                pendingDeleteIcon.setVisibility(View.GONE);
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
                
                // 显示收藏标记
                String filePath = item.getFile().getAbsolutePath();
                if (favoriteManager != null && favoriteManager.isFavorite(filePath)) {
                    favoriteIcon.setVisibility(View.VISIBLE);
                    favoriteIcon.setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN);
                } else {
                    favoriteIcon.setVisibility(View.GONE);
                }
                
                // 显示待删除标记
                if (pendingDeleteManager != null && pendingDeleteManager.isPendingDelete(filePath)) {
                    pendingDeleteIcon.setVisibility(View.VISIBLE);
                    pendingDeleteIcon.setColorFilter(Color.parseColor("#FF6B6B"), PorterDuff.Mode.SRC_IN);
                } else {
                    pendingDeleteIcon.setVisibility(View.GONE);
                }
            }
        }
    }
}

