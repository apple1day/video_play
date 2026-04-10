package androidx.media3.demo.media;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 待删除视频列表 Fragment
 * 显示所有被标记为待删除的视频，右上角有全部删除按钮
 */
public class PendingDeleteFragment extends Fragment {

    private static final String TAG = "PendingDeleteFragment";

    private RecyclerView recyclerView;
    private View emptyView;
    private TextView countText;
    private Button deleteAllButton;
    private PendingDeleteListAdapter adapter;
    private PendingDeleteManager pendingDeleteManager;
    private PendingDeleteManager.OnFileDeletedListener pendingDeleteListener;
    private List<PendingVideoItem> pendingItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_pending_delete, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        countText = view.findViewById(R.id.count_text);
        deleteAllButton = view.findViewById(R.id.delete_all_button);

        pendingDeleteManager = PendingDeleteManager.getInstance(getContext());

        setupRecyclerView();
        setupDeleteAllButton();
        setupPendingDeleteListener();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPendingFiles();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PendingDeleteListAdapter(pendingItems, item -> {
            // 点击播放视频
            if (item.getFile().exists()) {
                Intent intent = new Intent(getContext(), PlayerActivity.class);
                intent.putExtra("file_path", item.getFile().getAbsolutePath());
                ArrayList<String> fileList = new ArrayList<>();
                for (PendingVideoItem pi : pendingItems) {
                    fileList.add(pi.getFile().getAbsolutePath());
                }
                intent.putStringArrayListExtra("file_list", fileList);
                int index = fileList.indexOf(item.getFile().getAbsolutePath());
                intent.putExtra("current_index", index >= 0 ? index : 0);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "文件不存在", Toast.LENGTH_SHORT).show();
                loadPendingFiles();
            }
        }, item -> {
            // 长按：取消待删除标记或立即删除
            showItemOptions(item);
            return true;
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupDeleteAllButton() {
        deleteAllButton.setOnClickListener(v -> {
            int count = pendingItems.size();
            if (count == 0) {
                Toast.makeText(getContext(), "没有待删除的视频", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(getContext())
                .setTitle("确认全部删除")
                .setMessage("确定要删除所有 " + count + " 个待删除视频吗？\n此操作无法撤销。")
                .setPositiveButton("全部删除", (dialog, which) -> {
                    pendingDeleteManager.deleteAllPending();
                    Toast.makeText(getContext(), "正在删除 " + count + " 个视频...", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
        });
    }

    private void setupPendingDeleteListener() {
        pendingDeleteListener = new PendingDeleteManager.OnFileDeletedListener() {
            @Override
            public void onFileDeleted(String filePath, boolean success, String errorMessage) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> loadPendingFiles());
                }
            }

            @Override
            public void onFilesDeleted(int successCount, int failCount, List<String> failedFiles) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> loadPendingFiles());
                }
            }
        };
        pendingDeleteManager.addOnFileDeletedListener(pendingDeleteListener);
    }

    private void showItemOptions(PendingVideoItem item) {
        if (getContext() == null) return;

        String[] options = {"取消待删除标记", "立即删除", "查看信息"};
        new AlertDialog.Builder(getContext())
            .setTitle(item.getName())
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // 取消待删除标记
                        pendingDeleteManager.removePendingDelete(item.getFile().getAbsolutePath());
                        Toast.makeText(getContext(), "已取消待删除标记", Toast.LENGTH_SHORT).show();
                        loadPendingFiles();
                        break;
                    case 1: // 立即删除
                        confirmDeleteSingle(item);
                        break;
                    case 2: // 查看信息
                        showVideoInfo(item);
                        break;
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void confirmDeleteSingle(PendingVideoItem item) {
        new AlertDialog.Builder(getContext())
            .setTitle("确认删除")
            .setMessage("确定要立即删除 \"" + item.getName() + "\" 吗？\n此操作无法撤销。")
            .setPositiveButton("删除", (dialog, which) -> {
                new Thread(() -> {
                    File file = item.getFile();
                    boolean deleted = file.delete();
                    if (deleted || !file.exists()) {
                        pendingDeleteManager.removePendingDelete(file.getAbsolutePath());
                        // 通过 ContentResolver 删除 MediaStore 记录，而非 scanFile（避免产生0字节残留）
                        try {
                            android.net.Uri collection = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                                ? android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
                                : android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                            requireContext().getContentResolver().delete(
                                collection,
                                android.provider.MediaStore.Video.Media.DATA + "=?",
                                new String[]{file.getAbsolutePath()}
                            );
                        } catch (Exception ignored) {}
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (deleted || !file.exists()) {
                                Toast.makeText(getContext(), "已删除: " + item.getName(), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), "删除失败: " + item.getName(), Toast.LENGTH_SHORT).show();
                            }
                            loadPendingFiles();
                        });
                    }
                }).start();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showVideoInfo(PendingVideoItem item) {
        File file = item.getFile();
        String info = "文件名: " + item.getName() + "\n"
            + "大小: " + item.getFormattedSize() + "\n"
            + "路径: " + file.getAbsolutePath() + "\n"
            + "是否存在: " + (file.exists() ? "是" : "否（已删除）");

        new AlertDialog.Builder(getContext())
            .setTitle("视频信息")
            .setMessage(info)
            .setPositiveButton("确定", null)
            .show();
    }

    /**
     * 加载待删除文件列表
     * 自动过滤掉不存在的文件和0字节残留文件，并从待删除列表中清除这些无效条目
     */
    public void loadPendingFiles() {
        List<String> pendingPaths = pendingDeleteManager.getPendingDeleteList();
        pendingItems.clear();
        List<String> toRemove = new ArrayList<>();
        for (String path : pendingPaths) {
            File file = new File(path);
            // 过滤不存在的文件或0字节残留文件
            if (!file.exists() || file.length() == 0) {
                toRemove.add(path);
                continue;
            }
            pendingItems.add(new PendingVideoItem(file));
        }
        // 从 SharedPreferences 中清除无效条目
        for (String path : toRemove) {
            pendingDeleteManager.removePendingDelete(path);
        }

        adapter.updateItems(new ArrayList<>(pendingItems));
        updateUI();
    }

    private void updateUI() {
        int count = pendingItems.size();
        if (count == 0) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            countText.setText("暂无待删除视频");
            deleteAllButton.setEnabled(false);
            deleteAllButton.setAlpha(0.5f);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            countText.setText("共 " + count + " 个待删除视频");
            deleteAllButton.setEnabled(true);
            deleteAllButton.setAlpha(1.0f);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingDeleteManager != null && pendingDeleteListener != null) {
            pendingDeleteManager.removeOnFileDeletedListener(pendingDeleteListener);
            pendingDeleteListener = null;
        }
    }

    /**
     * 待删除视频数据模型
     */
    public static class PendingVideoItem {
        private final File file;

        public PendingVideoItem(File file) {
            this.file = file;
        }

        public File getFile() {
            return file;
        }

        public String getName() {
            return file.getName();
        }

        public String getFormattedSize() {
            return FileItem.formatSize(file.length());
        }

        public long getLastModified() {
            return file.lastModified();
        }
    }
}
