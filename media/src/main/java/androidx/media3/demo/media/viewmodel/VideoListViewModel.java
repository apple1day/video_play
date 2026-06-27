package androidx.media3.demo.media.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.demo.media.FileItem;
import androidx.media3.demo.media.data.VideoRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频主页 ViewModel。
 *
 * 旋转屏 / Fragment 重建时，扫描结果不丢；
 * 同样一份扫描结果可以被多个观察者订阅（HomeFragment + PlayerActivity 的预加载监听）。
 *
 * 状态分两类：
 *  - `allFiles` / `allVideos` —— "原始数据"，仅在 Repository 扫描完更新。
 *  - HomeFragment 仍然自行维护 currentFilter / currentSort / 选择模式等 UI 状态。
 */
public class VideoListViewModel extends AndroidViewModel {

    public final MutableLiveData<List<FileItem>> allFiles = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<List<FileItem>> allVideos = new MutableLiveData<>(new ArrayList<>());
    public final MutableLiveData<VideoRepository.LoadPhase> phase =
            new MutableLiveData<>(VideoRepository.LoadPhase.IDLE);
    /** 扫描中的增量。仅作为通知，值是这一批本身。 */
    public final MutableLiveData<List<FileItem>> incrementalBatch = new MutableLiveData<>();

    private final VideoRepository repo;
    private boolean hasLoadedOnce = false;

    public VideoListViewModel(@NonNull Application app) {
        super(app);
        this.repo = VideoRepository.get(app);
    }

    /** Fragment 首次创建 / 权限授予后调用。同步触发一次扫描；若已有结果可直接读 LiveData。 */
    public void loadVideos(FileItem.FileType filter, boolean forceRefresh) {
        if (forceRefresh) repo.invalidateCache();
        repo.loadVideos(filter, forceRefresh, new VideoRepository.ScanCallback() {
            @Override public void onPhaseChanged(VideoRepository.LoadPhase p) {
                phase.setValue(p);
            }
            @Override public void onBatchLoaded(List<FileItem> batch) {
                incrementalBatch.setValue(batch);
            }
            @Override public void onScanComplete(List<FileItem> files, List<FileItem> videos) {
                allFiles.setValue(files);
                allVideos.setValue(videos);
                hasLoadedOnce = true;
            }
        });
    }

    public boolean hasLoadedOnce() { return hasLoadedOnce; }
}
