package androidx.media3.demo.media;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
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

    /**
     * 使用 DiffUtil 精确更新列表，避免全量刷新导致的闪烁和性能损耗
     */
    public void updateItems(List<PendingDeleteFragment.PendingVideoItem> newItems) {
        final List<PendingDeleteFragment.PendingVideoItem> oldItems = new ArrayList<>(this.items);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).getFile().getAbsolutePath()
                        .equals(newItems.get(newItemPosition).getFile().getAbsolutePath());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                PendingDeleteFragment.PendingVideoItem oldItem = oldItems.get(oldItemPosition);
                PendingDeleteFragment.PendingVideoItem newItem = newItems.get(newItemPosition);
                return oldItem.getFile().length() == newItem.getFile().length()
                        && oldItem.getLastModified() == newItem.getLastModified();
            }
        });
        this.items.clear();
        this.items.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
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
                // 使用 getBindingAdapterPosition() 替代已弃用的 getAdapterPosition()
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onItemClick(items.get(pos));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
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
                // 使用 PorterDuffColorFilter 替代已弃用的 setColorFilter(int, PorterDuff.Mode)
                thumbnail.setColorFilter(new PorterDuffColorFilter(
                    Color.parseColor("#FFCCCC"), PorterDuff.Mode.SRC_IN));
            }
        }
    }
}
