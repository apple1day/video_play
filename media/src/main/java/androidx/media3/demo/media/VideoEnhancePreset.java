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
 *  - 标准：不做额外调色，保持原始输出（但仍激活 OpenGL pipeline 以支持 HDR→SDR tone mapping）
 *  - 鲜艳：提升对比度 + 饱和度，视觉冲击力更强（类似 VLC "全彩"）
 *  - 柔和：轻微降低对比度，护眼模式
 *
 * <p><b>注意：</b>必须使用 {@link #buildEffectsForPipeline()} 而非 {@link #buildEffects()} 传给
 * {@code player.setVideoEffects()}，以保证 OpenGL pipeline 始终被激活（STANDARD 预设需注入
 * No-op Contrast(0) 来触发 pipeline 初始化，否则 {@link ToneMappingVideoRenderer} 的
 * setRequestOpenGlToneMapping 在 registerInput() 阶段失效，导致 HDR 内容黑屏）。
 */
@UnstableApi
public enum VideoEnhancePreset {

    /** 标准：不做额外调色，但通过 No-op Contrast(0) 激活 OpenGL pipeline 以支持 tone mapping。 */
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

    /** 构建该预设的原始 Effect 列表（STANDARD 返回空列表）。 */
    public abstract ImmutableList<Effect> buildEffects();

    /**
     * 构建用于传给 {@code player.setVideoEffects()} 的 Effect 列表。
     *
     * <p>与 {@link #buildEffects()} 的区别：STANDARD 预设会注入一个 No-op {@code Contrast(0)}
     * 以确保 OpenGL effects pipeline 被激活，使 {@link ToneMappingVideoRenderer} 的
     * HDR→SDR tone mapping 生效。非 STANDARD 预设直接返回原列表。
     */
    public ImmutableList<Effect> buildEffectsForPipeline() {
        ImmutableList<Effect> effects = buildEffects();
        if (effects.isEmpty()) {
            // 注入 No-op effect，isNoOp()==true 时 GPU 不做额外处理，
            // 但 pipeline 已初始化，ToneMappingVideoRenderer 的 tone mapping 可正常生效。
            return ImmutableList.of(new Contrast(0f));
        }
        return effects;
    }

    public static VideoEnhancePreset fromOrdinal(int ordinal) {
        VideoEnhancePreset[] values = values();
        if (ordinal < 0 || ordinal >= values.length) return STANDARD;
        return values[ordinal];
    }
}
