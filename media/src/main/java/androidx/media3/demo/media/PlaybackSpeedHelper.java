package androidx.media3.demo.media;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.media3.common.PlaybackParameters;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * 播放速度辅助类
 */
public class PlaybackSpeedHelper {
    
    private static final String PREF_NAME = "playback_speed";
    private static final String KEY_SPEED = "speed";
    private static final float DEFAULT_SPEED = 1.0f;
    
    // 预设速度选项
    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final String[] SPEED_LABELS = {
        "0.5x (慢速)",
        "0.75x (较慢)", 
        "1.0x (正常)",
        "1.25x (较快)",
        "1.5x (快速)",
        "1.75x (更快)",
        "2.0x (极速)"
    };
    
    /**
     * 显示速度选择对话框
     */
    public static void showSpeedDialog(Context context, ExoPlayer player) {
        if (player == null) {
            return;
        }
        
        float currentSpeed = player.getPlaybackParameters().speed;
        int currentIndex = getSpeedIndex(currentSpeed);
        
        new AlertDialog.Builder(context)
            .setTitle("播放速度")
            .setSingleChoiceItems(SPEED_LABELS, currentIndex, (dialog, which) -> {
                float newSpeed = SPEED_OPTIONS[which];
                setPlaybackSpeed(context, player, newSpeed);
                dialog.dismiss();
                
                Toast.makeText(context, 
                    "播放速度: " + SPEED_LABELS[which], 
                    Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 设置播放速度
     */
    public static void setPlaybackSpeed(Context context, ExoPlayer player, float speed) {
        if (player == null) {
            return;
        }
        
        PlaybackParameters parameters = new PlaybackParameters(speed);
        player.setPlaybackParameters(parameters);
        
        // 保存速度设置
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_SPEED, speed).apply();
    }
    
    /**
     * 获取保存的播放速度
     */
    public static float getSavedSpeed(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_SPEED, DEFAULT_SPEED);
    }
    
    /**
     * 恢复播放速度
     */
    public static void restorePlaybackSpeed(Context context, ExoPlayer player) {
        float savedSpeed = getSavedSpeed(context);
        if (savedSpeed != DEFAULT_SPEED) {
            setPlaybackSpeed(context, player, savedSpeed);
        }
    }
    
    /**
     * 循环切换速度（快捷操作）
     */
    public static void cycleSpeed(Context context, ExoPlayer player) {
        if (player == null) {
            return;
        }
        
        float currentSpeed = player.getPlaybackParameters().speed;
        int currentIndex = getSpeedIndex(currentSpeed);
        int nextIndex = (currentIndex + 1) % SPEED_OPTIONS.length;
        float newSpeed = SPEED_OPTIONS[nextIndex];
        
        setPlaybackSpeed(context, player, newSpeed);
        
        Toast.makeText(context, 
            "播放速度: " + SPEED_LABELS[nextIndex], 
            Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 获取当前速度的索引
     */
    private static int getSpeedIndex(float speed) {
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            if (Math.abs(SPEED_OPTIONS[i] - speed) < 0.01f) {
                return i;
            }
        }
        return 2; // 默认返回1.0x
    }
    
    /**
     * 获取速度标签
     */
    public static String getSpeedLabel(float speed) {
        int index = getSpeedIndex(speed);
        return SPEED_LABELS[index];
    }
}
