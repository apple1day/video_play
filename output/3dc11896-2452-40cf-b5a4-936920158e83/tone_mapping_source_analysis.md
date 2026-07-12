# Tone Mapping 关键源码分析报告

> 项目：`/Users/houjiaying/code/codebuddy_media` (androidx media3)
> 分析范围：ExoPlayer 中 HDR→SDR OpenGL tone mapping 的 API 入口、触发条件与核心实现

---

## 1. 文件清单与路径

| 文件 | 绝对路径 |
|------|----------|
| PlaybackVideoGraphWrapper.java | `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/PlaybackVideoGraphWrapper.java` |
| ExoPlayer.java (接口) | `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java` |
| ExoPlayerImpl.java (实现) | `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java` |
| MediaCodecVideoRenderer.java | `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java` |
| VideoSink.java (接口) | `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/VideoSink.java` |
| Effect.java (接口) | `libraries/common/src/main/java/androidx/media3/common/Effect.java` |
| Renderer.java (MSG 常量) | `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/Renderer.java` |

---

## 2. 如何通过 API 启用 HDR→SDR 的 OpenGL Tone Mapping

### 2.1 核心 API：`ExoPlayer.setVideoEffects(List<Effect>)`

**接口定义** — `ExoPlayer.java:1683-1717`

```java
/**
 * Sets a {@link List} of {@linkplain Effect video effects} that will be applied to each video
 * frame.
 *
 * <p>...limitations...
 * <li>This feature works only with the default {@link MediaCodecVideoRenderer} and not custom
 *     or extension {@linkplain Renderer video renderers}.
 * <li>This method must be called at least once before calling {@link #prepare()} (in order to
 *     set up the effects pipeline). The effects can be changed during playback by subsequent
 *     calls to this method after {@link #prepare()}.
 *
 * @param videoEffects The {@link List} of {@linkplain Effect video effects} to apply.
 */
@UnstableApi
void setVideoEffects(List<Effect> videoEffects);   // 行 1717
```

**参数类型**：`List<Effect>`，其中 `Effect` 是 `androidx.media3.common.Effect` 标记接口（`Effect.java:23`），仅有 `getDurationAfterEffectApplied` 默认方法。

**实现** — `ExoPlayerImpl.java:1399-1410`

```java
@SuppressWarnings("ReturnValueIgnored")
@Override
public void setVideoEffects(List<Effect> videoEffects) {
  verifyApplicationThread();
  try {
    // LINT.IfChange(set_video_effects)
    Class.forName("androidx.media3.effect.SingleInputVideoGraph$Factory")
        .getConstructor(VideoFrameProcessor.Factory.class);
    // LINT.ThenChange(video/PlaybackVideoGraphWrapper.java)
  } catch (ClassNotFoundException | NoSuchMethodException e) {
    throw new IllegalStateException("Could not find required lib-effect dependencies.", e);
  }
  sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_VIDEO_EFFECTS, videoEffects);
}
```

**关键点**：
- 通过反射检查 `androidx.media3.effect.SingleInputVideoGraph$Factory` 是否在运行时类路径上（即需要 `media3-effect` 依赖），否则抛 `IllegalStateException`。
- 通过 `sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_VIDEO_EFFECTS, videoEffects)` 向视频 Renderer 发送消息，`MSG_SET_VIDEO_EFFECTS = 13`（定义于 `Renderer.java:348`）。

### 2.2 HDR→SDR Tone Mapping 的触发方式

**关键发现**：在当前代码版本中，`PlaybackVideoGraphWrapper.setRequestOpenGlToneMapping(boolean)` 和 `setIsInputSdrToneMapped(boolean)` 是 **public 方法**，但在 `libraries/` 源码中**没有任何生产代码调用它们**（仅在测试 `PlaybackVideoGraphWrapperTest.java:154,186` 中使用）。

这意味着 **HDR→SDR 的 OpenGL tone mapping 不是通过显式 API 调用启用的，而是在 `PlaybackVideoGraphWrapper.registerInput()` 中根据输入颜色信息自动判断触发的**（见第 3 节）。

---

## 3. PlaybackVideoGraphWrapper 中 Tone Mapping 的触发条件

### 3.1 Tone Mapping 相关字段 — `PlaybackVideoGraphWrapper.java:310-311`

```java
private boolean requestOpenGlToneMapping;      // 行 310
private boolean isInputSdrToneMapped;           // 行 311
```

### 3.2 Setter 方法 — `PlaybackVideoGraphWrapper.java:463-471`

```java
/** Sets whether to tone map the input video with OpenGL. */
public void setRequestOpenGlToneMapping(boolean requestOpenGlToneMapping) {
  this.requestOpenGlToneMapping = requestOpenGlToneMapping;
}

/** Sets whether the input should be treated as if it has been tone mapped to SDR. */
public void setIsInputSdrToneMapped(boolean isInputSdrToneMapped) {
  this.isInputSdrToneMapped = isInputSdrToneMapped;
}
```

### 3.3 Tone Mapping 触发逻辑 — `registerInput()` 方法，行 559-612

这是**核心触发点**，在注册 primary input（inputIndex==0）时决定输出颜色信息：

```java
private boolean registerInput(Format sourceFormat, int inputIndex)
    throws VideoSink.VideoSinkException {
  if (inputIndex == PRIMARY_SEQUENCE_INDEX) {
    checkState(state == STATE_CREATED);
    ColorInfo inputColorInfo = getAdjustedInputColorInfo(sourceFormat.colorInfo);  // 行 563
    ColorInfo outputColorInfo;
    try {
      if (requestOpenGlToneMapping) {                           // 条件1：显式请求
        outputColorInfo = ColorInfo.SDR_BT709_LIMITED;          // → HDR 转 SDR
      } else if (inputColorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
          && SDK_INT < 34
          && GlUtil.isBt2020PqExtensionSupported()) {           // 条件2：HLG + API<34 + PQ支持
        // PQ SurfaceView 从 API 33 支持，HLG 输出从 API 34 支持
        // 因此将 HLG 转为 PQ 以便 API 33 正确显示
        outputColorInfo =
            inputColorInfo.buildUpon().setColorTransfer(C.COLOR_TRANSFER_ST2084).build();
      } else if (!GlUtil.isColorTransferSupported(inputColorInfo.colorTransfer)
          && SDK_INT >= 29) {                                    // 条件3：颜色转换不支持 + API≥29
        Log.w(TAG, "Color transfer %d is not supported. Falling back to OpenGl tone mapping.");
        outputColorInfo = ColorInfo.SDR_BT709_LIMITED;          // → 回退到 SDR tone mapping
      } else if (inputColorInfo.colorTransfer == C.COLOR_TRANSFER_SRGB
          || inputColorInfo.colorTransfer == C.COLOR_TRANSFER_GAMMA_2_2) {  // 条件4：SDR输入
        outputColorInfo = ColorInfo.SDR_BT709_LIMITED;
      } else {
        outputColorInfo = inputColorInfo;                        // 条件5：保持原样
      }
    } catch (GlException e) {
      throw new VideoSink.VideoSinkException(e, sourceFormat);
    }
    // ...创建 videoGraph，传入 outputColorInfo...
    videoGraph = videoGraphFactory.create(
        context, outputColorInfo, ...);                          // 行 598-606
```

### 3.4 `getAdjustedInputColorInfo()` — 行 739-745

```java
private ColorInfo getAdjustedInputColorInfo(@Nullable ColorInfo inputColorInfo) {
  if (inputColorInfo == null || !inputColorInfo.isDataSpaceValid() || isInputSdrToneMapped) {
    return ColorInfo.SDR_BT709_LIMITED;
  }
  return inputColorInfo;
}
```

当 `isInputSdrToneMapped == true` 时，输入颜色信息被强制设为 `SDR_BT709_LIMITED`。

### 3.5 Tone Mapping 触发条件汇总

| 条件 | 触发场景 | 输出 ColorInfo |
|------|----------|----------------|
| `requestOpenGlToneMapping == true` | 显式请求 OpenGL tone mapping | `SDR_BT709_LIMITED` (HDR→SDR) |
| `isInputSdrToneMapped == true` | 输入已被 tone mapped 到 SDR | 输入强制为 `SDR_BT709_LIMITED` |
| HLG + API<34 + PQ扩展支持 | HLG 在 API 33 无法直接输出 | 转为 ST2084 (PQ) |
| 颜色转换不支持 + API≥29 | 设备不支持该 colorTransfer | `SDR_BT709_LIMITED` (回退到 SDR) |
| SRGB / GAMMA_2_2 输入 | SDR 输入 | `SDR_BT709_LIMITED` |
| 其他 | HDR 且设备支持 | 保持原输入 |

**结论**：`requestOpenGlToneMapping` 是强制 HDR→SDR tone mapping 的开关，但当前版本中**没有生产代码路径自动设置它为 true**。实际 HDR→SDR tone mapping 主要在"设备不支持颜色转换"时作为回退策略自动触发（条件3）。

---

## 4. MediaCodecVideoRenderer 中的 Video Effects / Tone Mapping 代码

### 4.1 videoEffects 字段 — `MediaCodecVideoRenderer.java:222`

```java
private @MonotonicNonNull List<Effect> videoEffects;
```

### 4.2 消息处理 — `MediaCodecVideoRenderer.java:1215-1219`

```java
case MSG_SET_VIDEO_EFFECTS:
  @SuppressWarnings("unchecked")
  List<Effect> videoEffects = (List<Effect>) checkNotNull(message);
  setVideoEffects(videoEffects);
  break;
```

### 4.3 setVideoEffects 方法 — `MediaCodecVideoRenderer.java:1524-1538`

```java
/** Sets the {@linkplain Effect video effects} to apply. */
public void setVideoEffects(List<Effect> effects) {
  if (effects.equals(VideoFrameProcessor.REDRAW)) {
    if (videoSink == null || !videoSink.isInitialized()) {
      return;
    }
    videoSink.redraw();
    return;
  }
  videoEffects = effects;
  if (videoSink != null) {
    videoSink.setVideoEffects(effects);
  }
}
```

特殊处理：传入 `VideoFrameProcessor.REDRAW` 时触发立即重绘而非设置 effects。

### 4.4 VideoSink 创建（启用 effects 时）— `MediaCodecVideoRenderer.java:887-893`

```java
if (!hasSetVideoSink) {
  if (videoEffects != null && videoSink == null) {
    PlaybackVideoGraphWrapper playbackVideoGraphWrapper =
        createPlaybackVideoGraphWrapper(context, videoFrameReleaseControl);
    playbackVideoGraphWrapper.setTotalVideoInputCount(1);
    videoSink = playbackVideoGraphWrapper.getSink(/* inputIndex= */ 0);
  }
  hasSetVideoSink = true;
}
```

**关键**：只有当 `videoEffects != null`（即调用了 `setVideoEffects`）时，才会创建 `PlaybackVideoGraphWrapper` 及其 `VideoSink`。这正是启用 effects pipeline（含 tone mapping 能力）的入口。

### 4.5 createPlaybackVideoGraphWrapper — `MediaCodecVideoRenderer.java:976-988`

```java
protected PlaybackVideoGraphWrapper createPlaybackVideoGraphWrapper(
    Context context, VideoFrameReleaseControl videoFrameReleaseControl) {
  return new PlaybackVideoGraphWrapper.Builder(context, videoFrameReleaseControl)
      .setEnablePlaylistMode(true)
      .experimentalSetLateThresholdToDropInputUs(...)
      .setClock(getClock())
      .build();
}
```

注意：此默认实现**未调用** `setRequestOpenGlToneMapping(true)`，因此默认不强制 tone mapping。子类可覆盖此方法来启用。

### 4.6 configureVideoSink — `MediaCodecVideoRenderer.java:917-973`

在 renderer enable 时配置 VideoSink，行 971-973 将 effects 传递给 sink：

```java
if (videoEffects != null) {
  videoSink.setVideoEffects(videoEffects);
}
```

### 4.7 changeVideoSinkInputStream — `MediaCodecVideoRenderer.java:1801-1813`

流切换时将 effects 传递给 sink：

```java
protected void changeVideoSinkInputStream(...) {
  List<Effect> videoEffectsToApply = videoEffects != null ? videoEffects : ImmutableList.of();
  videoSink.onInputStreamChanged(inputType, format, ..., videoEffectsToApply);
}
```

---

## 5. VideoSink 接口中与 Tone Mapping / Effects 相关的方法

**文件**：`VideoSink.java`（共 324 行，接口定义）

| 方法 | 行号 | 说明 |
|------|------|------|
| `void setVideoEffects(List<Effect> videoEffects)` | 221 | 设置立即应用的 video effects |
| `void redraw()` | 169 | 立即重绘 video effects |
| `void onInputStreamChanged(int, Format, long, int, List<Effect>)` | 258-263 | 通知新输入流，携带 `List<Effect> videoEffects` 参数 |
| `boolean initialize(Format sourceFormat)` | 163 | 初始化 sink（触发 graph 创建与 tone mapping 判断） |

`VideoSink` 接口本身**不直接包含 tone mapping 方法**。Tone mapping 逻辑封装在 `PlaybackVideoGraphWrapper`（`VideoSink` 的提供者）内部的 `registerInput()` 中，当 `VideoSink.initialize()` 被调用时间接触发。

---

## 6. Effect 接口定义

**文件**：`Effect.java`（共 32 行）

```java
package androidx.media3.common;

/** Marker interface for a video frame effect. */
@UnstableApi
public interface Effect {
  default long getDurationAfterEffectApplied(long durationUs) {
    return durationUs;
  }
}
```

这是一个**标记接口**，具体 Effect 实现位于 `media3-effect` 模块（如 `androidx.media3.effect` 包下的各种效果类）。`setVideoEffects` 接收 `List<Effect>`，用户可传入自定义或内置的 effect 实例。

---

## 7. 完整调用链路总结

```
应用层
  └─ ExoPlayer.setVideoEffects(List<Effect>)                    [ExoPlayer.java:1717]
       └─ ExoPlayerImpl.setVideoEffects()                        [ExoPlayerImpl.java:1399]
            ├─ 反射检查 media3-effect 依赖
            └─ sendRendererMessage(MSG_SET_VIDEO_EFFECTS=13, effects)
                 └─ MediaCodecVideoRenderer.handleMessage()      [行 1215]
                      └─ MediaCodecVideoRenderer.setVideoEffects()  [行 1525]
                           ├─ videoEffects = effects (保存)
                           └─ if videoSink != null → videoSink.setVideoEffects()

Renderer enable 时:
  MediaCodecVideoRenderer.onEnabled()                            [行 887]
    └─ if videoEffects != null && videoSink == null:
         └─ createPlaybackVideoGraphWrapper()                    [行 889-892]
              └─ PlaybackVideoGraphWrapper.Builder().build()
         └─ videoSink = playbackVideoGraphWrapper.getSink(0)

初始化时 (VideoSink.initialize → registerInput):
  PlaybackVideoGraphWrapper.registerInput()                      [行 559]
    ├─ getAdjustedInputColorInfo()                               [行 563, 739]
    │    └─ if isInputSdrToneMapped → SDR_BT709_LIMITED
    ├─ 判断 outputColorInfo (requestOpenGlToneMapping 等)         [行 566-591]
    └─ videoGraphFactory.create(context, outputColorInfo, ...)   [行 598]
         └─ VideoGraph 根据 outputColorInfo 决定是否做 tone mapping
```

---

## 8. 关键发现与注意事项

1. **启用 effects 即启用 tone mapping 能力**：调用 `setVideoEffects()` 会触发 `PlaybackVideoGraphWrapper` 创建，该 wrapper 内置 HDR→SDR tone mapping 的自动回退逻辑。

2. **强制 HDR→SDR 的方式**：当前版本需通过覆盖 `MediaCodecVideoRenderer.createPlaybackVideoGraphWrapper()` 调用 `setRequestOpenGlToneMapping(true)`，或在 `PlaybackVideoGraphWrapper` 实例上直接设置。无公开 ExoPlayer API 直接请求强制 tone mapping。

3. **自动 tone mapping 触发**：当设备不支持输入的 colorTransfer（如某些 HDR 格式）且 API≥29 时，自动回退到 OpenGL SDR tone mapping。

4. **依赖要求**：使用 `setVideoEffects` 必须在运行时类路径包含 `media3-effect` 模块（版本需与 exoplayer 一致）。

5. **时机要求**：`setVideoEffects` 必须在 `prepare()` 之前至少调用一次以建立 effects pipeline；之后可在播放中动态修改。

6. **限制**：不支持自定义 Renderer、不支持修改帧时间戳的 Effect、不支持 DRM 内容。
