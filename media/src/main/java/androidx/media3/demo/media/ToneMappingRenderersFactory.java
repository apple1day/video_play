package androidx.media3.demo.media;

import android.content.Context;
import android.os.Handler;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import java.util.ArrayList;

/**
 * 自定义 RenderersFactory，用 {@link ToneMappingVideoRenderer} 替换默认的
 * {@link MediaCodecVideoRenderer}，以启用 HDR→SDR tone mapping。
 *
 * <p>保留了 {@link DefaultRenderersFactory} 的所有其他行为：扩展渲染器模式、解码器回退、
 * 音频/文本/元数据渲染器等，仅替换视频渲染器实例。
 */
@UnstableApi
public class ToneMappingRenderersFactory extends DefaultRenderersFactory {

  public ToneMappingRenderersFactory(Context context) {
    super(context);
    setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON);
    setEnableDecoderFallback(true);
  }

  @Override
  protected void buildVideoRenderers(
      Context context,
      @ExtensionRendererMode int extensionRendererMode,
      MediaCodecSelector mediaCodecSelector,
      boolean enableDecoderFallback,
      Handler eventHandler,
      VideoRendererEventListener eventListener,
      long allowedVideoJoiningTimeMs,
      ArrayList<Renderer> out) {
    // 使用自定义的 ToneMappingVideoRenderer 替代默认的 MediaCodecVideoRenderer
    ToneMappingVideoRenderer.Builder videoRendererBuilder =
        new ToneMappingVideoRenderer.Builder(context)
            .setCodecAdapterFactory(getCodecAdapterFactory())
            .setMediaCodecSelector(mediaCodecSelector)
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
    out.add(videoRendererBuilder.build());

    // 保留扩展渲染器逻辑（VP9/AV1 软解等），与父类行为一致
    if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
      return;
    }
    int extensionRendererIndex = out.size();
    if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
      extensionRendererIndex--;
    }

    try {
      Class<?> clazz = Class.forName("androidx.media3.decoder.vp9.LibvpxVideoRenderer");
      @SuppressWarnings("rawtypes")
      java.lang.reflect.Constructor constructor =
          clazz.getConstructor(
              long.class,
              Handler.class,
              androidx.media3.exoplayer.video.VideoRendererEventListener.class,
              int.class);
      Renderer renderer =
          (Renderer)
              constructor.newInstance(
                  allowedVideoJoiningTimeMs,
                  eventHandler,
                  eventListener,
                  MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
      out.add(extensionRendererIndex++, renderer);
    } catch (Exception e) {
      // 扩展解码器不存在时跳过，与父类行为一致
    }

    try {
      Class<?> clazz = Class.forName("androidx.media3.decoder.av1.Libdav1dVideoRenderer");
      @SuppressWarnings("rawtypes")
      java.lang.reflect.Constructor constructor =
          clazz.getConstructor(
              boolean.class,
              Handler.class,
              androidx.media3.exoplayer.video.VideoRendererEventListener.class,
              int.class);
      Renderer renderer =
          (Renderer)
              constructor.newInstance(
                  enableDecoderFallback,
                  eventHandler,
                  eventListener,
                  MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
      out.add(extensionRendererIndex++, renderer);
    } catch (Exception e) {
      // 扩展解码器不存在时跳过，与父类行为一致
    }
  }
}
