package androidx.media3.demo.media;

import android.util.Log;
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

    private static final String TAG = "FolderAdapter";
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
        Log.d(TAG, "updateFolders called, old size: " + this.folders.size() + ", new size: " + newFolders.size());
        
        // 直接更新列表并通知刷新（DiffUtil在某些情况下可能不触发更新）
        this.folders.clear();
        this.folders.addAll(newFolders);
        notifyDataSetChanged();
        
        Log.d(TAG, "updateFolders completed, current size: " + this.folders.size() + ", getItemCount: " + getItemCount());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder called");
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_folder, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder called for position: " + position);
        FolderItem folder = folders.get(position);
        holder.bind(folder);
    }

    @Override
    public int getItemCount() {
        int count = folders.size();
        Log.d(TAG, "getItemCount called, returning: " + count);
        return count;
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
