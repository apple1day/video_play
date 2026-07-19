package androidx.media3.demo.media;

import android.content.Context;
import android.view.Display;
import android.view.WindowManager;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.PlaybackVideoGraphWrapper;
import androidx.media3.exoplayer.video.VideoFrameReleaseControl;

/**
 * 自定义视频渲染器，强制启用 HDR→SDR OpenGL tone mapping。
 *
 * <p>当设备屏幕不支持 HDR 时，ExoPlayer 默认行为是在解码器支持 colorTransfer 的情况下直接
 * 输出 HDR 信号，导致 SDR 屏幕上画面发灰、色彩平淡。此渲染器通过覆盖
 * {@link #createPlaybackVideoGraphWrapper} 方法，在创建 {@link PlaybackVideoGraphWrapper} 后
 * 调用 {@link PlaybackVideoGraphWrapper#setRequestOpenGlToneMapping(boolean)} 强制将 HDR 内容
 * 通过 OpenGL 转换为 SDR BT.709，显著改善 SDR 屏幕上的 HDR 视频观感。
 *
 * <p>仅在 SDR 屏幕上启用 tone mapping，HDR 屏幕保持原始 HDR 直出以获得最佳效果。
 */
@UnstableApi
public class ToneMappingVideoRenderer extends MediaCodecVideoRenderer {

  public ToneMappingVideoRenderer(Builder builder) {
    super(builder);
  }

  @Override
  protected PlaybackVideoGraphWrapper createPlaybackVideoGraphWrapper(
      Context context, VideoFrameReleaseControl videoFrameReleaseControl) {
    PlaybackVideoGraphWrapper wrapper =
        super.createPlaybackVideoGraphWrapper(context, videoFrameReleaseControl);

    // 仅在 SDR 屏幕上强制 tone mapping；HDR 屏幕保持原始 HDR 输出
    if (!isHdrDisplay(context)) {
      wrapper.setRequestOpenGlToneMapping(true);
    }

    return wrapper;
  }

  /**
   * 判断设备屏幕是否支持 HDR。
   *
   * <p>通过检查屏幕的 {@link Display#isWideColorGamut()} 和 HDR capabilities 来判断。
   * 如果屏幕不支持广色域或没有任何 HDR 类型支持，则视为 SDR 屏幕。
   */
  private static boolean isHdrDisplay(Context context) {
    try {
      Display display =
          ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
      if (display == null) {
        return false;
      }
      // HDR 屏幕必须支持广色域，且 HDR capabilities 中有支持的 HDR 类型
      if (!display.isWideColorGamut()) {
        return false;
      }
      Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();
      if (hdrCapabilities == null) {
        return false;
      }
      int[] supportedTypes = hdrCapabilities.getSupportedHdrTypes();
      return supportedTypes != null && supportedTypes.length > 0;
    } catch (Exception e) {
      // 出现任何异常时保守地视为 SDR 屏幕，启用 tone mapping
      return false;
    }
  }
}
