package androidx.media3.demo.media;

import androidx.media3.common.Effect;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;

import com.google.common.collect.ImmutableList;

/**
 * 视频画质增强预设。
 *
 * 通过 Media3 Effect pipeline（OpenGL GPU 处理）对播放画面进行实时调色：
 *  - 标准：不做任何处理，保持原始输出
 *  - 鲜艳：提升对比度 + 饱和度，视觉冲击力更强（类似 VLC "全彩"）
 *  - 柔和：轻微提升亮度，降低对比度，护眼模式
 */
@UnstableApi
public enum VideoEnhancePreset {

    /** 标准：不添加任何 effect，与原始解码输出一致。 */
    STANDARD("标准") {
        @Override
        public ImmutableList<Effect> buildEffects() {
            return ImmutableList.of();
        }
    },

    /**
     * 鲜艳：对比度 +0.15，饱和度 +20。
     * 参考：VLC Android "全彩" / MX Player 色彩增强默认值。
     * 本地文件场景下几乎无额外 CPU 开销（全部 GPU 处理）。
     */
    VIVID("鲜艳") {
        @Override
        public ImmutableList<Effect> buildEffects() {
            return ImmutableList.of(
                new Contrast(0.15f),
                new HslAdjustment.Builder()
                    .adjustSaturation(20f)   // 饱和度 +20（范围 -100~100）
                    .build()
            );
        }
    },

    /**
     * 柔和：对比度 -0.08，降低刺激感，适合暗光环境长时间观看。
     */
    SOFT("柔和") {
        @Override
        public ImmutableList<Effect> buildEffects() {
            return ImmutableList.of(
                new Contrast(-0.08f)
            );
        }
    };

    public final String label;

    VideoEnhancePreset(String label) {
        this.label = label;
    }

    /** 构建该预设对应的 Effect 列表，直接传给 {@code player.setVideoEffects()}。 */
    public abstract ImmutableList<Effect> buildEffects();

    public static VideoEnhancePreset fromOrdinal(int ordinal) {
        VideoEnhancePreset[] values = values();
        if (ordinal < 0 || ordinal >= values.length) return STANDARD;
        return values[ordinal];
    }
}
