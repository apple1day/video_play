package androidx.media3.demo.media;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件列表缓存管理器。
 *
 * 改用磁盘文件（JSON）替换 SharedPreferences，消除原版 500 条截断和大字符串塞 SP 的性能问题。
 * 缓存有效期 30 分钟；通过检查首尾各 5 个文件的存在性和修改时间来快速判断是否失效。
 */
public class FileListCacheManager {

    private static final String TAG = "FileListCacheManager";
    private static final String CACHE_FILE_NAME = "video_list_cache.json";
    private static final String META_FILE_NAME  = "video_list_meta.json";

    // 缓存有效期：30 分钟
    private static final long CACHE_VALIDITY_MS = 30 * 60 * 1000L;
    // 快速验证时抽查的文件数量（头 + 尾各几个）
    private static final int SPOT_CHECK_COUNT = 5;

    private static FileListCacheManager instance;
    private final File cacheFile;
    private final File metaFile;

    private FileListCacheManager(Context context) {
        File dir = context.getApplicationContext().getCacheDir();
        cacheFile = new File(dir, CACHE_FILE_NAME);
        metaFile  = new File(dir, META_FILE_NAME);
    }

    public static synchronized FileListCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new FileListCacheManager(context);
        }
        return instance;
    }

    /**
     * 将完整文件列表写入磁盘缓存（应在 IO 线程调用）。
     *
     * @param fileList         要缓存的文件列表（无数量上限）
     * @param isInternalStorage 保留参数，兼容旧调用方，现在统一缓存所有存储结果
     */
    public void saveFileList(List<FileItem> fileList, boolean isInternalStorage) {
        try {
            JSONArray array = new JSONArray();
            for (FileItem item : fileList) {
                JSONObject o = new JSONObject();
                o.put("path", item.getFile().getAbsolutePath());
                o.put("lastModified", item.getLastModified());
                array.put(o);
            }
            writeText(cacheFile, array.toString());

            JSONObject meta = new JSONObject();
            meta.put("saveTime", System.currentTimeMillis());
            meta.put("count", fileList.size());
            writeText(metaFile, meta.toString());

            Log.d(TAG, "缓存写入完成：" + fileList.size() + " 个文件");
        } catch (JSONException | IOException e) {
            Log.e(TAG, "保存缓存失败", e);
        }
    }

    /**
     * 从磁盘缓存加载文件列表（应在 IO 线程调用）。
     * 返回 null 表示缓存不存在、已过期或失效。
     *
     * @param isInternalStorage 保留参数，兼容旧调用方，现在忽略
     */
    public List<FileItem> loadFileList(boolean isInternalStorage) {
        // 1. 检查元数据
        if (!metaFile.exists() || !cacheFile.exists()) {
            Log.d(TAG, "缓存文件不存在");
            return null;
        }
        try {
            JSONObject meta = new JSONObject(readText(metaFile));
            long saveTime = meta.optLong("saveTime", 0);
            if (System.currentTimeMillis() - saveTime > CACHE_VALIDITY_MS) {
                Log.d(TAG, "缓存已过期");
                return null;
            }

            // 2. 解析列表
            JSONArray array = new JSONArray(readText(cacheFile));
            int total = array.length();
            if (total == 0) return null;

            // 3. 抽查首尾各 SPOT_CHECK_COUNT 个文件
            if (!spotCheck(array, 0, Math.min(SPOT_CHECK_COUNT, total))) return null;
            if (total > SPOT_CHECK_COUNT) {
                if (!spotCheck(array, Math.max(0, total - SPOT_CHECK_COUNT), total)) return null;
            }

            // 4. 全量构建 FileItem 列表
            List<FileItem> result = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                JSONObject o = array.getJSONObject(i);
                File f = new File(o.getString("path"));
                if (f.exists()) result.add(new FileItem(f));
            }
            Log.d(TAG, "缓存命中，加载 " + result.size() + " 个文件（原始 " + total + " 条）");
            return result.isEmpty() ? null : result;

        } catch (JSONException | IOException e) {
            Log.e(TAG, "读取缓存失败", e);
            return null;
        }
    }

    /** 清除所有缓存文件。 */
    public void clearCache() {
        //noinspection ResultOfMethodCallIgnored
        cacheFile.delete();
        //noinspection ResultOfMethodCallIgnored
        metaFile.delete();
        Log.d(TAG, "缓存已清除");
    }

    /** 快速检查缓存是否仍然有效（只读元数据）。 */
    public boolean isCacheValid(boolean isInternalStorage) {
        if (!metaFile.exists() || !cacheFile.exists()) return false;
        try {
            JSONObject meta = new JSONObject(readText(metaFile));
            long saveTime = meta.optLong("saveTime", 0);
            return System.currentTimeMillis() - saveTime <= CACHE_VALIDITY_MS;
        } catch (Exception e) {
            return false;
        }
    }

    // ── 私有工具 ──────────────────────────────────────────────────

    /** 抽查 [from, to) 区间内的条目：文件必须存在且修改时间未变。 */
    private boolean spotCheck(JSONArray array, int from, int to) throws JSONException {
        for (int i = from; i < to; i++) {
            JSONObject o = array.getJSONObject(i);
            File f = new File(o.getString("path"));
            if (!f.exists()) {
                Log.d(TAG, "缓存失效（文件不存在）: " + f.getName());
                return false;
            }
            long cachedMod = o.optLong("lastModified", -1);
            if (cachedMod != -1 && f.lastModified() != cachedMod) {
                Log.d(TAG, "缓存失效（文件已修改）: " + f.getName());
                return false;
            }
        }
        return true;
    }

    private static void writeText(File file, String text) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, false))) {
            w.write(text);
        }
    }

    private static String readText(File file) throws IOException {
        StringBuilder sb = new StringBuilder((int) file.length());
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
