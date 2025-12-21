package androidx.media3.demo.filebrowser;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 收藏管理器 - 使用 SharedPreferences 存储收藏的视频
 */
public class FavoriteManager {
    
    private static final String PREF_NAME = "favorites";
    private static final String KEY_FAVORITE_FILES = "favorite_files";
    
    private static FavoriteManager instance;
    private SharedPreferences preferences;
    
    private FavoriteManager(Context context) {
        preferences = context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized FavoriteManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoriteManager(context);
        }
        return instance;
    }
    
    /**
     * 添加收藏
     */
    public void addFavorite(String filePath) {
        Set<String> favorites = getFavorites();
        favorites.add(filePath);
        saveFavorites(favorites);
    }
    
    /**
     * 移除收藏
     */
    public void removeFavorite(String filePath) {
        Set<String> favorites = getFavorites();
        favorites.remove(filePath);
        saveFavorites(favorites);
    }
    
    /**
     * 切换收藏状态
     * @return 切换后的状态：true = 已收藏，false = 未收藏
     */
    public boolean toggleFavorite(String filePath) {
        if (isFavorite(filePath)) {
            removeFavorite(filePath);
            return false;
        } else {
            addFavorite(filePath);
            return true;
        }
    }
    
    /**
     * 检查是否已收藏
     */
    public boolean isFavorite(String filePath) {
        return getFavorites().contains(filePath);
    }
    
    /**
     * 获取所有收藏
     */
    public Set<String> getFavorites() {
        return new HashSet<>(preferences.getStringSet(KEY_FAVORITE_FILES, new HashSet<>()));
    }
    
    /**
     * 保存收藏列表
     */
    private void saveFavorites(Set<String> favorites) {
        preferences.edit()
            .putStringSet(KEY_FAVORITE_FILES, favorites)
            .apply();
    }
    
    /**
     * 清空所有收藏
     */
    public void clearAllFavorites() {
        preferences.edit()
            .remove(KEY_FAVORITE_FILES)
            .apply();
    }
}
