package androidx.media3.demo.media;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PendingDeleteListAdapter extends RecyclerView.Adapter<PendingDeleteListAdapter.ViewHolder> {

    private List<PendingDeleteFragment.PendingVideoItem> items = new ArrayList<>();
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;

    public interface OnItemClickListener {
        void onItemClick(PendingDeleteFragment.PendingVideoItem item);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(PendingDeleteFragment.PendingVideoItem item);
    }

    public PendingDeleteListAdapter(List<PendingDeleteFragment.PendingVideoItem> items,
                                    OnItemClickListener clickListener,
                                    OnItemLongClickListener longClickListener) {
        this.items = new ArrayList<>(items);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void updateItems(List<PendingDeleteFragment.PendingVideoItem> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_pending_delete, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PendingDeleteFragment.PendingVideoItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView thumbnail;
        private TextView fileName;
        private TextView fileInfo;
        private TextView statusBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            fileName = itemView.findViewById(R.id.file_name);
            fileInfo = itemView.findViewById(R.id.file_info);
            statusBadge = itemView.findViewById(R.id.status_badge);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onItemClick(items.get(pos));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
                    return longClickListener.onItemLongClick(items.get(pos));
                }
                return false;
            });
        }

        public void bind(PendingDeleteFragment.PendingVideoItem item) {
            fileName.setText(item.getName());

            // 文件信息
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String info = item.getFormattedSize() + "  •  " + sdf.format(new Date(item.getLastModified()));
            fileInfo.setText(info);

            // 文件是否存在的状态标识
            File file = item.getFile();
            if (!file.exists()) {
                statusBadge.setText("文件已不存在");
                statusBadge.setVisibility(View.VISIBLE);
                statusBadge.setBackgroundColor(Color.parseColor("#FFCCCC"));
                statusBadge.setTextColor(Color.parseColor("#CC0000"));
                itemView.setAlpha(0.6f);
            } else {
                statusBadge.setText("待删除");
                statusBadge.setVisibility(View.VISIBLE);
                statusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"));
                statusBadge.setTextColor(Color.parseColor("#D32F2F"));
                itemView.setAlpha(1.0f);
            }

            // 加载缩略图
            if (thumbnail != null && file.exists()) {
                ThumbnailLoader.loadThumbnail(itemView.getContext(), file, thumbnail);
                thumbnail.clearColorFilter();
            } else if (thumbnail != null) {
                thumbnail.setImageResource(android.R.drawable.ic_menu_delete);
                thumbnail.setColorFilter(Color.parseColor("#FFCCCC"), android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
    }
}
