package androidx.media3.demo.filebrowser;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MoreFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout storageInfoLayout;
    private FileBrowserAdapter adapter;
    private File currentDirectory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_more, container, false);
        
        recyclerView = view.findViewById(R.id.recycler_view);
        storageInfoLayout = view.findViewById(R.id.storage_info_layout);

        adapter = new FileBrowserAdapter(new ArrayList<>(), item -> {
            if (item.isDirectory()) {
                navigateToDirectory(item.getFile());
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        if (PermissionHelper.hasMediaPermissions(getActivity())) {
            loadStorageInfo();
            loadRootDirectories();
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
            List<File> storageDirectories = FileUtils.getStorageDirectories(getContext());
            List<FileUtils.StorageInfo> storageInfos = new ArrayList<>();
            
            for (File directory : storageDirectories) {
                storageInfos.add(FileUtils.getStorageInfo(directory));
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                storageInfoLayout.removeAllViews();
                
                for (int i = 0; i < storageDirectories.size(); i++) {
                    File directory = storageDirectories.get(i);
                    FileUtils.StorageInfo info = storageInfos.get(i);
                    
                    View infoView = createStorageInfoView(
                        directory.equals(android.os.Environment.getDataDirectory()) ? 
                            getString(R.string.internal_storage) : getString(R.string.external_storage),
                        info
                    );
                    storageInfoLayout.addView(infoView);
                }
            });
        }).start();
    }

    private View createStorageInfoView(String title, FileUtils.StorageInfo info) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 8, 0, 8);
        
        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setPadding(0, 0, 0, 4);
        layout.addView(titleView);
        
        TextView totalView = new TextView(getContext());
        totalView.setText(getString(R.string.total_space) + ": " + info.getFormattedTotal());
        totalView.setTextSize(14);
        layout.addView(totalView);
        
        TextView usedView = new TextView(getContext());
        usedView.setText(getString(R.string.used_space) + ": " + info.getFormattedUsed());
        usedView.setTextSize(14);
        layout.addView(usedView);
        
        TextView freeView = new TextView(getContext());
        freeView.setText(getString(R.string.free_space) + ": " + info.getFormattedFree());
        freeView.setTextSize(14);
        layout.addView(freeView);
        
        return layout;
    }

    private void loadRootDirectories() {
        new Thread(() -> {
            List<File> storageDirectories = FileUtils.getStorageDirectories(getContext());
            List<FileItem> items = new ArrayList<>();
            
            for (File directory : storageDirectories) {
                items.add(new FileItem(directory));
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.updateFiles(items);
                currentDirectory = null;
            });
        }).start();
    }

    private void navigateToDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
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
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                adapter.updateFiles(items);
            });
        }).start();
    }
}

