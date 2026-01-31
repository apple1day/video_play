package androidx.media3.demo.media;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹列表适配器
 */
public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    private List<FolderItem> folders = new ArrayList<>();
    private OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(FolderItem folder);
    }

    public FolderAdapter(List<FolderItem> folders, OnFolderClickListener listener) {
        this.folders = folders;
        this.listener = listener;
    }

    public void updateFolders(List<FolderItem> newFolders) {
        // 使用DiffUtil计算差异，避免全量刷新
        List<FolderItem> oldFolders = this.folders;
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldFolders.size();
            }

            @Override
            public int getNewListSize() {
                return newFolders.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                FolderItem oldItem = oldFolders.get(oldItemPosition);
                FolderItem newItem = newFolders.get(newItemPosition);
                return oldItem.getFolderPath().equals(newItem.getFolderPath());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                FolderItem oldItem = oldFolders.get(oldItemPosition);
                FolderItem newItem = newFolders.get(newItemPosition);
                return oldItem.getFolderPath().equals(newItem.getFolderPath()) &&
                       oldItem.getVideoCount() == newItem.getVideoCount() &&
                       oldItem.getTotalSize() == newItem.getTotalSize();
            }
        });
        
        this.folders = newFolders;
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_folder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FolderItem folder = folders.get(position);
        holder.bind(folder);
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView folderName;
        private TextView folderInfo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            folderName = itemView.findViewById(R.id.folder_name);
            folderInfo = itemView.findViewById(R.id.folder_info);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFolderClick(folders.get(position));
                }
            });
        }

        public void bind(FolderItem folder) {
            folderName.setText(folder.getFolderName());
            
            String info = folder.getVideoCount() + " 个视频  " + folder.getFormattedSize();
            folderInfo.setText(info);
        }
    }
}
