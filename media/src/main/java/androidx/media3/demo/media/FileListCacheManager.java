package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件列表缓存管理器
 * 用于缓存扫描到的视频文件列表，避免每次都重新扫描文件系统
 */
public class FileListCacheManager {
    
    private static final String TAG = "FileListCacheManager";
    private static final String PREFS_NAME = "file_list_cache";
    private static final String KEY_FILE_LIST = "cached_file_list";
    private static final String KEY_CACHE_TIME = "cache_time";
    private static final String KEY_STORAGE_TYPE = "storage_type";
    
    // 缓存有效期：30分钟
    private static final long CACHE_VALIDITY = 30 * 60 * 1000;
    
    private static FileListCacheManager instance;
    private SharedPreferences prefs;
    
    private FileListCacheManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized FileListCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new FileListCacheManager(context);
        }
        return instance;
    }
    
    /**
     * 保存文件列表到缓存
     */
    public void saveFileList(List<FileItem> fileList, boolean isInternalStorage) {
        try {
            JSONArray jsonArray = new JSONArray();
            
            for (FileItem item : fileList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("path", item.getFile().getAbsolutePath());
                jsonObject.put("name", item.getName());
                jsonObject.put("size", item.getSize());
                jsonObject.put("lastModified", item.getLastModified());
                jsonObject.put("type", item.getFileType().name());
                jsonArray.put(jsonObject);
            }
            
            prefs.edit()
                .putString(KEY_FILE_LIST, jsonArray.toString())
                .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                .putBoolean(KEY_STORAGE_TYPE, isInternalStorage)
                .apply();
            
            Log.d(TAG, "文件列表已缓存: " + fileList.size() + " 个文件");
        } catch (JSONException e) {
            Log.e(TAG, "保存缓存失败", e);
        }
    }
    
    /**
     * 从缓存加载文件列表
     * 如果缓存过期或不存在，返回null
     */
    public List<FileItem> loadFileList(boolean isInternalStorage) {
        // 检查存储类型是否匹配
        boolean cachedStorageType = prefs.getBoolean(KEY_STORAGE_TYPE, true);
        if (cachedStorageType != isInternalStorage) {
            Log.d(TAG, "存储类型不匹配，不使用缓存");
            return null;
        }
        
        // 检查缓存是否过期
        long cacheTime = prefs.getLong(KEY_CACHE_TIME, 0);
        long currentTime = System.currentTimeMillis();
        if (currentTime - cacheTime > CACHE_VALIDITY) {
            Log.d(TAG, "缓存已过期");
            return null;
        }
        
        String jsonString = prefs.getString(KEY_FILE_LIST, null);
        if (jsonString == null) {
            Log.d(TAG, "没有缓存数据");
            return null;
        }
        
        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            List<FileItem> fileList = new ArrayList<>();
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                
                String path = jsonObject.getString("path");
                File file = new File(path);
                
                // 检查文件是否仍然存在
                if (!file.exists()) {
                    continue;
                }
                
                // 检查文件修改时间是否变化
                long cachedModified = jsonObject.getLong("lastModified");
                if (file.lastModified() != cachedModified) {
                    // 文件已被修改，缓存失效
                    Log.d(TAG, "文件已被修改，缓存失效: " + file.getName());
                    return null;
                }
                
                // FileItem 构造函数会自动从 file 对象提取所有信息
                FileItem item = new FileItem(file);
                fileList.add(item);
            }
            
            Log.d(TAG, "从缓存加载了 " + fileList.size() + " 个文件");
            return fileList;
            
        } catch (JSONException e) {
            Log.e(TAG, "解析缓存失败", e);
            return null;
        }
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        prefs.edit()
            .remove(KEY_FILE_LIST)
            .remove(KEY_CACHE_TIME)
            .remove(KEY_STORAGE_TYPE)
            .apply();
        Log.d(TAG, "缓存已清除");
    }
    
    /**
     * 检查缓存是否有效
     */
    public boolean isCacheValid(boolean isInternalStorage) {
        boolean cachedStorageType = prefs.getBoolean(KEY_STORAGE_TYPE, true);
        if (cachedStorageType != isInternalStorage) {
            return false;
        }
        
        long cacheTime = prefs.getLong(KEY_CACHE_TIME, 0);
        long currentTime = System.currentTimeMillis();
        return (currentTime - cacheTime <= CACHE_VALIDITY) && 
               prefs.contains(KEY_FILE_LIST);
    }
}
