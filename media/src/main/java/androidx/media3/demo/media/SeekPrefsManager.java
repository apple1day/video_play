package androidx.media3.demo.media;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 视频快进/快退偏好设置管理器。
 *
 * 快进模式（seekMode）：
 *   MODE_PERCENT  —— 按视频总时长百分比快进（默认，范围 1%–3%）
 *   MODE_FIXED    —— 固定秒数（根据滑动距离映射 15s–50s）
 *
 * 参数说明：
 *   seekPercent     —— MODE_PERCENT 时单次快进的百分比（1–3，默认 2）
 *   longPressSpeed  —— 长按快进倍速（1.5x / 2.0x / 3.0x，默认 2.0x）
 */
public class SeekPrefsManager {

    public static final int MODE_PERCENT = 0; // 按百分比
    public static final int MODE_FIXED   = 1; // 固定秒数

    private static final String PREFS_NAME       = "seek_prefs";
    private static final String KEY_SEEK_MODE    = "seek_mode";
    private static final String KEY_SEEK_PERCENT = "seek_percent";   // 1–3
    private static final String KEY_LONG_SPEED   = "long_press_speed"; // 乘以 10 存储，如 20 = 2.0x

    // 固定模式：滑动距离映射到快进秒数的区间
    public static final int FIXED_MIN_SEC = 15;
    public static final int FIXED_MAX_SEC = 50;

    private static SeekPrefsManager instance;
    private final SharedPreferences prefs;

    private SeekPrefsManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SeekPrefsManager getInstance(Context context) {
        if (instance == null) instance = new SeekPrefsManager(context);
        return instance;
    }

    // ── 快进模式 ──────────────────────────────────────────────────

    public int getSeekMode() {
        return prefs.getInt(KEY_SEEK_MODE, MODE_PERCENT);
    }

    public void setSeekMode(int mode) {
        prefs.edit().putInt(KEY_SEEK_MODE, mode).apply();
    }

    // ── 百分比模式参数 ────────────────────────────────────────────

    /** 单次快进百分比，范围 1–3，默认 2。 */
    public int getSeekPercent() {
        return prefs.getInt(KEY_SEEK_PERCENT, 2);
    }

    public void setSeekPercent(int percent) {
        percent = Math.max(1, Math.min(3, percent));
        prefs.edit().putInt(KEY_SEEK_PERCENT, percent).apply();
    }

    // ── 长按倍速 ─────────────────────────────────────────────────

    /** 长按快进倍速，默认 2.0x，存储为整数（乘以 10）。 */
    public float getLongPressSpeed() {
        int stored = prefs.getInt(KEY_LONG_SPEED, 20);
        return stored / 10.0f;
    }

    /** @param speed 支持 1.5f / 2.0f / 3.0f */
    public void setLongPressSpeed(float speed) {
        prefs.edit().putInt(KEY_LONG_SPEED, Math.round(speed * 10)).apply();
    }

    // ── 快进量计算工具 ───────────────────────────────────────────

    /**
     * 根据当前模式计算一次快进的毫秒数。
     *
     * @param durationMs     视频总时长（毫秒）
     * @param swipeRatio     滑动距离占屏幕宽度的比例（0–1），仅在 MODE_FIXED 时生效
     * @param forward        true=快进，false=快退（不影响数值，只用于日志）
     * @return 快进毫秒数（正整数）
     */
    public long calcSeekMs(long durationMs, float swipeRatio, boolean forward) {
        if (getSeekMode() == MODE_PERCENT) {
            // 百分比模式：单次固定按 seekPercent% 快进
            double ratio = getSeekPercent() / 100.0;
            return Math.max(1000, (long) (durationMs * ratio));
        } else {
            // 固定模式：根据滑动比例在 15s–50s 线性插值
            float clamped = Math.max(0f, Math.min(1f, swipeRatio));
            long sec = FIXED_MIN_SEC + Math.round(clamped * (FIXED_MAX_SEC - FIXED_MIN_SEC));
            return sec * 1000L;
        }
    }

    /**
     * 生成显示给用户的快进提示文字。
     * @param seekMs   快进毫秒数
     * @param forward  是否快进（false=快退）
     */
    public static String buildOverlayText(long seekMs, boolean forward) {
        long sec = seekMs / 1000;
        if (forward) return "快进" + sec + "秒 ▶▶";
        else         return "◀◀ 快退" + sec + "秒";
    }
}
