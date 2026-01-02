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

