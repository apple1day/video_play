# HDR / Tone Mapping 代码搜索报告

**项目路径**: `/Users/houjiaying/code/codebuddy_media`  
**搜索时间**: 2026-07-12  
**搜索范围**: 项目全目录（含 `libraries/` 子目录），排除 `.git`、`build`、`.gradle`、`.idea`、`output`、`buildout`

---

## 搜索关键词总览

| # | 关键词 | 匹配文件数 | 备注 |
|---|--------|-----------|------|
| 1 | `tone`（不区分大小写） | 大量（含 `seekToNext` 等无关匹配） | 需过滤，真正相关见下文 |
| 2 | `HDR`（不区分大小写） | ~20 个源文件 + RELEASENOTES | |
| 3 | `SdrToneMapping` | 1（仅 RELEASENOTES） | |
| 4 | `VideoEffects` / `videoEffects` | ~8 个源文件 + RELEASENOTES | |
| 5 | `setVideoEffects` | ~6 个源文件 + RELEASENOTES | |
| 6 | `MediaCodec` + tone mapping | 0（直接组合无匹配） | 见 MediaCodec/HDR 分析 |
| 7 | `ColorInfo` | ~15 个源文件 + RELEASENOTES | |
| 8 | `requestSdrToneMapping` | 1（仅 RELEASENOTES） | |

---

## 1. `tone`（不区分大小写）— Tone Mapping 相关匹配

> 说明：`tone` 的全量搜索包含大量无关匹配（`seekToNext`、`moveToNext`、`setOnEventListener`、`RINGTONE` 等）。以下仅列出与 **tone mapping / 色调映射** 真正相关的结果。

### 1.1 Tone Mapping 核心实现（`PlaybackVideoGraphWrapper.java`）

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/PlaybackVideoGraphWrapper.java`

| 行号 | 内容 |
|------|------|
| 310 | `private boolean requestOpenGlToneMapping;` |
| 311 | `private boolean isInputSdrToneMapped;` |
| 463 | `/** Sets whether to tone map the input video with OpenGL. */` |
| 464 | `public void setRequestOpenGlToneMapping(boolean requestOpenGlToneMapping) {` |
| 465 | `this.requestOpenGlToneMapping = requestOpenGlToneMapping;` |
| 468 | `/** Sets whether the input should be treated as if it has been tone mapped to SDR. */` |
| 469 | `public void setIsInputSdrToneMapped(boolean isInputSdrToneMapped) {` |
| 470 | `this.isInputSdrToneMapped = isInputSdrToneMapped;` |
| 566 | `if (requestOpenGlToneMapping) {` |
| 576 | `// Force OpenGL tone mapping if the GL extension required to output HDR colors is not` |
| 577 | `// available. OpenGL tone mapping is only supported on API 29+.` |
| 583 | `"Color transfer %d is not supported. Falling back to OpenGl tone mapping.",` |
| 740 | `if (inputColorInfo == null \|\| !inputColorInfo.isDataSpaceValid() \|\| isInputSdrToneMapped) {` |

### 1.2 Tone Mapping 测试（`PlaybackVideoGraphWrapperTest.java`）

**文件**: `libraries/exoplayer/src/test/java/androidx/media3/exoplayer/video/PlaybackVideoGraphWrapperTest.java`

| 行号 | 内容 |
|------|------|
| 111 | `public void onInputStreamChanged_withNoToneMapping_initializesGraphWithInputColorInfo()` |
| 139 | `public void onInputStreamChanged_withInputSdrToneMapped_initializesGraphWithBt709Input()` |
| 154 | `playbackVideoGraphWrapper.setIsInputSdrToneMapped(true);` |
| 171 | `public void onInputStreamChanged_withOpenGlToneMapping_initializesGraphWithInputColorInfo()` |
| 186 | `playbackVideoGraphWrapper.setRequestOpenGlToneMapping(true);` |

### 1.3 其他 tone 相关

**文件**: `libraries/common/src/main/java/androidx/media3/common/C.java`

| 行号 | 内容 |
|------|------|
| 1281 | `for some SDR use-cases like tone-mapping.`（注释，`COLOR_TRANSFER_SDR` 定义附近）|

---

## 2. `HDR`（不区分大小写）

### 2.1 核心定义 — `ColorInfo.java`

**文件**: `libraries/common/src/main/java/androidx/media3/common/ColorInfo.java`

| 行号 | 内容摘要 |
|------|---------|
| 46 | `@Nullable private byte[] hdrStaticInfo;` |
| 115 | `/** Sets the HdrStaticInfo as defined in CTA-861.3. */` |
| 121 | `public Builder setHdrStaticInfo(@Nullable byte[] hdrStaticInfo)` |
| 199 | `&& colorInfo.hdrStaticInfo == null`（`isEquivalentToAssumedSdrDefault`）|
| 317 | `/** Returns whether the ColorInfo uses an HDR C.ColorTransfer. */` |
| 322 | `public static boolean isTransferHdr(@Nullable ColorInfo colorInfo)` |
| 338 | `@Nullable public final byte[] hdrStaticInfo;` |
| 534 | `private static final String FIELD_HDR_STATIC_INFO = ...` |

### 2.2 HDR 静态信息解析 — `BoxParser.java`（MP4）

**文件**: `libraries/extractor/src/main/java/androidx/media3/extractor/mp4/BoxParser.java`

| 行号 | 内容摘要 |
|------|---------|
| 1400 | `// hdrStaticInfo is allocated using allocate() in allocateHdrStaticInfo().` |
| 1465 | `// HDR related metadata.` |
| 1469 | `// The format of HDR static info is defined in CTA-861-G:2017, Table 45.` |
| 1470 | `@Nullable ByteBuffer hdrStaticInfo = null;` |
| 1656-1691 | `clli` / `mdcv` box 解析，写入 `hdrStaticInfo` |
| 1813 | `.setHdrStaticInfo(hdrStaticInfo != null ? hdrStaticInfo.array() : null)` |
| 2014 | `private static ByteBuffer allocateHdrStaticInfo()` |
| 2015 | `// For HDR static info, Android decoders expect a 25-byte array.` |

### 2.3 HDR 静态信息解析 — `MatroskaExtractor.java`（MKV）

**文件**: `libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java`

| 行号 | 内容摘要 |
|------|---------|
| 2222 | `/** Default max content light level (CLL) that should be encoded into hdrStaticInfo. */` |
| 2225 | `/** Default frame-average light level (FALL) that should be encoded into hdrStaticInfo. */` |
| 2537 | `@Nullable byte[] hdrStaticInfo = getHdrStaticInfo();` |
| 2543 | `.setHdrStaticInfo(hdrStaticInfo)` |
| 2635 | `/** Returns the HDR Static Info as defined in CTA-861.3. */` |
| 2637 | `private byte[] getHdrStaticInfo()` |
| 2652-2667 | 构建 25 字节 `hdrStaticInfo` 数据 |

### 2.4 HDR Codec Profile — `CodecSpecificDataUtil.java`

**文件**: `libraries/common/src/main/java/androidx/media3/common/util/CodecSpecificDataUtil.java`

| 行号 | 内容摘要 |
|------|---------|
| 593 | `profile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10;` |
| 596 | `// HDR. On Android 13+, the platform guarantees...` |
| 930 | `&& (colorInfo.hdrStaticInfo != null` |
| 933 | `profile = MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10;` |
| 971 | `// MediaCodecInfo.CodecProfileLevel.APVProfile422_10HDR10Plus...` |

### 2.5 HDR 显示能力 — `MediaCodecVideoRenderer.java`

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java`

| 行号 | 内容摘要 |
|------|---------|
| 48 | `import android.view.Display.HdrCapabilities;` |
| 218 | `private boolean codecHandlesHdr10PlusOutOfBandMetadata;` |
| 853 | `if (display == null \|\| !display.isHdr())` |
| 856 | `HdrCapabilities hdrCapabilities = display.getHdrCapabilities();` |
| 860 | `for (int hdrType : hdrCapabilities.getSupportedHdrTypes())` |
| 861 | `if (hdrType == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)` |
| 1548 | `codecHandlesHdr10PlusOutOfBandMetadata =` |
| 1549 | `checkNotNull(getCodecInfo()).isHdr10PlusOutOfBandMetadataSupported();` |
| 1815 | `// codecHandlesHdr10PlusOutOfBandMetadata is false if Util.SDK_INT < 29` |
| 1821 | `if (!codecHandlesHdr10PlusOutOfBandMetadata)` |
| 1826 | `// Check for HDR10+ out-of-band metadata. See ST 2094-40.` |
| 1840 | `byte[] hdr10PlusInfo = new byte[data.remaining()];` |
| 1843 | `setHdr10PlusInfoV29(checkNotNull(getCodec()), hdr10PlusInfo);` |
| 2393 | `private static void setHdr10PlusInfoV29(MediaCodecAdapter codec, byte[] hdr10PlusInfo)` |
| 2395 | `codecParameters.putByteArray(MediaCodec.PARAMETER_KEY_HDR10_PLUS_INFO, hdr10PlusInfo);` |

### 2.6 HDR Codec 信息 — `MediaCodecInfo.java`

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecInfo.java`

| 行号 | 内容摘要 |
|------|---------|
| 403 | `/** Whether the codec handles HDR10+ out-of-band metadata. */` |
| 404 | `public boolean isHdr10PlusOutOfBandMetadataSupported()` |
| 407 | `if (capabilities.profile == CodecProfileLevel.VP9Profile2HDR10Plus)` |

### 2.7 HDR GL 工具 — `GlUtil.java`

**文件**: `libraries/common/src/main/java/androidx/media3/common/util/GlUtil.java`

| 行号 | 内容摘要 |
|------|---------|
| 235 | `for HDR input.` |
| 373 | `// !isEncoderInputSurface means outputting to a display surface. HDR display surfaces` |
| 385 | `// TODO(b/262259999): HDR10 PQ content looks dark on the screen, on API 33.` |
| 446-448 | `EGL_CONFIG_ATTRIBUTES_RGBA_1010102 could be used for HDR input...` |
| 985 | `<p>HDR support is not yet implemented.` |

### 2.8 HDR Overlay — `OverlaySettings.java`

**文件**: `libraries/common/src/main/java/androidx/media3/common/OverlaySettings.java`

| 行号 | 内容摘要 |
|------|---------|
| 44 | `/** The default luminance multiplier of an SDR overlay when overlaid on a HDR frame. */` |
| 45 | `float DEFAULT_HDR_LUMINANCE_MULTIPLIER = 1f;` |
| 119 | `/** Returns the luminance multiplier of an SDR overlay when overlaid on a HDR frame. */` |
| 130 | `default float getHdrLuminanceMultiplier()` |

### 2.9 HDR MediaFormat — `MediaFormatUtil.java`

**文件**: `libraries/common/src/main/java/androidx/media3/common/util/MediaFormatUtil.java`

| 行号 | 内容摘要 |
|------|---------|
| 277 | `maybeSetByteBuffer(format, MediaFormat.KEY_HDR_STATIC_INFO, colorInfo.hdrStaticInfo);` |
| 303 | `ByteBuffer hdrStaticInfoByteBuffer = mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO);` |
| 305-321 | 从 MediaFormat 读取 `hdrStaticInfo` 并构建 `ColorInfo` |

### 2.10 HDR Player — `Player.java`

**文件**: `libraries/common/src/main/java/androidx/media3/common/Player.java`

| 行号 | 内容摘要 |
|------|---------|
| 3377 | `handling for HDR and secure content.`（注释，`VIDEO_OUTPUT_MODE_NONE` 附近）|

### 2.11 HDR 容器测试 — `NalUnitUtilTest.java` / `NalUnitUtil.java`

**文件**: `libraries/container/src/test/java/androidx/media3/container/NalUnitUtilTest.java`

| 行号 | 内容摘要 |
|------|---------|
| 80 | `private static final byte[] H265_VPS_TEST_DATA_2VIEWS_HDR =` |
| 87 | `private static final byte[] H265_SPS_TEST_DATA_2VIEWS_HDR_VIEW_0 =` |
| 96 | `private static final byte[] H265_SPS_TEST_DATA_2VIEWS_HDR_VIEW_1 =` |
| 102 | `private static final byte[] H265_SPS_TEST_DATA_2VIEWS_HDR_SEI =` |
| 325-394 | 相关测试方法 `parseH265VpsAndSpsNalUnits2ViewsHdr` |

**文件**: `libraries/container/src/main/java/androidx/media3/container/NalUnitUtil.java`

| 行号 | 内容摘要 |
|------|---------|
| 690 | `// risks around closed captions, HDR metadata in SEI messages.` |

### 2.12 AVI 容器 HDRL（非 HDR，但匹配大小写）

**文件**: `libraries/extractor/src/main/java/androidx/media3/extractor/avi/AviExtractor.java`

| 行号 | 内容摘要 |
|------|---------|
| 66, 102-104, 112-114, 159, 189, 197, 222-250, 329, 370-372 | AVI hdrl LIST chunk 解析（`FOURCC_hdrl`、`STATE_SKIPPING_TO_HDRL` 等）|

> 注：`hdrl` 是 AVI 文件格式的 header list 标识，与 HDR 视频无关。

---

## 3. `SdrToneMapping`

仅 **1 处** 匹配：

**文件**: `RELEASENOTES.md`

| 行号 | 内容 |
|------|------|
| 2913 | ``TransformationRequest.Builder.setEnableRequestSdrToneMapping(boolean)`` |

> 注：源码中未发现 `SdrToneMapping` 标识符，仅存在于 RELEASENOTES 历史记录中。源码中实际使用的是 `isInputSdrToneMapped`（见第 1 节）。

---

## 4. `VideoEffects` / `videoEffects`

### 4.1 接口定义 — `ExoPlayer.java`

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java`

| 行号 | 内容摘要 |
|------|---------|
| 1714 | `@param videoEffects The List of Effect to apply.` |
| 1717 | `void setVideoEffects(List<Effect> videoEffects);` |

### 4.2 实现类 — `ExoPlayerImpl.java` / `SimpleExoPlayer.java`

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java`

| 行号 | 内容摘要 |
|------|---------|
| 1399 | `public void setVideoEffects(List<Effect> videoEffects)` |
| 1409 | `sendRendererMessage(TRACK_TYPE_VIDEO, MSG_SET_VIDEO_EFFECTS, videoEffects);` |

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/SimpleExoPlayer.java`

| 行号 | 内容摘要 |
|------|---------|
| 629 | `public void setVideoEffects(List<Effect> videoEffects)` |
| 631 | `player.setVideoEffects(videoEffects);` |

### 4.3 Video Sink — `VideoSink.java` / `DefaultVideoSink.java`

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/VideoSink.java`

| 行号 | 内容摘要 |
|------|---------|
| 168 | `/** Redraws the setVideoEffects video effects immediately. */` |
| 221 | `void setVideoEffects(List<Effect> videoEffects);` |
| 256 | `@param videoEffects The List<Effect> to apply to the new stream.` |
| 263 | `List<Effect> videoEffects);` |

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/DefaultVideoSink.java`

| 行号 | 内容摘要 |
|------|---------|
| 179 | `public void setVideoEffects(List<Effect> videoEffects)` |
| 213 | `<p>videoEffects is required to be empty` |
| 221 | `List<Effect> videoEffects)` |
| 222 | `checkState(videoEffects.isEmpty());` |

### 4.4 Playback Video Graph — `PlaybackVideoGraphWrapper.java`

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/PlaybackVideoGraphWrapper.java`

| 行号 | 内容摘要 |
|------|---------|
| 167 | `// This is set to true for ExoPlayer.setVideoEffects(). It's always false in` |
| 753 | `/* videoEffects= */ ImmutableList.of());` |
| 768 | `private ImmutableList<Effect> videoEffects;` |
| 792 | `videoEffects = ImmutableList.of();` |
| 896 | `List<Effect> videoEffects)` |
| 898 | `this.videoEffects = ImmutableList.copyOf(videoEffects);` |
| 988 | `public void setVideoEffects(List<Effect> videoEffects)` |
| 989 | `if (this.videoEffects.equals(videoEffects))` |
| 992 | `this.videoEffects = ImmutableList.copyOf(videoEffects);` |
| 1159 | `videoEffects,` |

### 4.5 MediaCodec Video Renderer — `MediaCodecVideoRenderer.java`

**文件**: `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java`

| 行号 | 内容摘要 |
|------|---------|
| 222 | `private @MonotonicNonNull List<Effect> videoEffects;` |
| 888 | `if (videoEffects != null && videoSink == null)` |
| 971 | `if (videoEffects != null)` |
| 972 | `videoSink.setVideoEffects(videoEffects);` |
| 1217 | `List<Effect> videoEffects = (List<Effect>) checkNotNull(message);` |
| 1218 | `setVideoEffects(videoEffects);` |
| 1806 | `List<Effect> videoEffectsToApply = videoEffects != null ? videoEffects : ImmutableList.of();` |
| 1812 | `videoEffectsToApply);` |

### 4.6 Proguard 规则

**文件**: `libraries/exoplayer/proguard-rules.txt`

| 行号 | 内容摘要 |
|------|---------|
| 80 | `public void setVideoEffects(java.util.List);` |
| 87 | `public void setVideoEffects(java.util.List);` |

### 4.7 测试

**文件**: `libraries/exoplayer/src/test/java/androidx/media3/exoplayer/video/PlaybackVideoGraphWrapperTest.java`

| 行号 | 内容摘要 |
|------|---------|
| 74 | `public void onInputStreamChanged_setsVideoSinkVideoEffects()` |
| 132 | `/* videoEffects= */ ImmutableList.of());` |
| 161 | `/* videoEffects= */ ImmutableList.of());` |
| 193 | `/* videoEffects= */ ImmutableList.of());` |

---

## 5. `setVideoEffects`

与第 4 节重叠，此处列出所有 `setVideoEffects` 方法定义/调用点：

| 文件 | 行号 | 类型 |
|------|------|------|
| `libraries/exoplayer/proguard-rules.txt` | 80, 87 | keep 规则 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java` | 1717 | 接口声明 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java` | 1399 | 实现 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/SimpleExoPlayer.java` | 629, 631 | 实现 + 委托 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/PlaybackVideoGraphWrapper.java` | 988 | Builder 方法 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/DefaultVideoSink.java` | 179 | 实现 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/VideoSink.java` | 221 | 接口声明 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java` | 1525 | 实现；972, 1218, 1536 | 调用 |
| `RELEASENOTES.md` | 1044, 1341, 2087, 3050 | 历史记录 |

---

## 6. `MediaCodec` + Tone Mapping

**直接组合搜索无匹配**（`MediaCodec` 与 `tone` 出现在同一行的结果为 0）。

但 `MediaCodec` 与 HDR/tone mapping 存在间接关联：

| 文件 | 行号 | 关联说明 |
|------|------|---------|
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/MediaCodecVideoRenderer.java` | 48, 218, 853-861, 1548-1549, 1815-1843, 2393-2395 | MediaCodec HDR10+ 参数传递、HDR 显示能力检测 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/video/PlaybackVideoGraphWrapper.java` | 566-583 | tone mapping 回退逻辑（当 HDR color transfer 不支持时回退到 OpenGL tone mapping）|
| `libraries/common/src/main/java/androidx/media3/common/util/MediaFormatUtil.java` | 277 | `MediaFormat.KEY_HDR_STATIC_INFO` 设置到 MediaCodec |
| `libraries/common/src/main/java/androidx/media3/common/util/CodecSpecificDataUtil.java` | 593, 933 | HDR10 profile 映射 |
| `libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/MediaCodecInfo.java` | 403-407 | HDR10+ out-of-band metadata 能力查询 |

> **架构说明**: `MediaCodecVideoRenderer` 使用 `MediaCodec` 进行硬件解码，HDR 元数据通过 `MediaFormat.KEY_HDR_STATIC_INFO` 和 `MediaCodec.PARAMETER_KEY_HDR10_PLUS_INFO` 传递给解码器。Tone mapping 逻辑不在 `MediaCodecVideoRenderer` 内部，而在 `PlaybackVideoGraphWrapper` 中通过 OpenGL 实现——当解码器输出 HDR color transfer 不被 GL 支持时，回退到 OpenGL tone mapping 将 HDR 转为 SDR。

---

## 7. `ColorInfo`

`ColorInfo` 是 HDR/tone mapping 体系的核心数据结构。匹配分布如下：

### 7.1 核心定义 — `ColorInfo.java`

**文件**: `libraries/common/src/main/java/androidx/media3/common/ColorInfo.java`

关键行号：30, 34, 37-40, 46, 59-66, 115-122, 150-153, 158-201, 317-338, 352, 427, 431, 443, 453, 460, 534, 543, 549-555

关键 API：
- `ColorInfo.Builder`（行 37）
- `setHdrStaticInfo(byte[])`（行 121）
- `isTransferHdr(ColorInfo)`（行 322）— 判断是否 HDR transfer
- `isEquivalentToAssumedSdrDefault(ColorInfo)`（行 188）
- `SDR_BT709_LIMITED`（行 158）— tone mapping 输出目标
- `SRGB_BT709_FULL`（行 169）
- `fromBundle(Bundle)` / `toBundle()`（行 550, 543）

### 7.2 Format 中的 ColorInfo — `Format.java`

**文件**: `libraries/common/src/main/java/androidx/media3/common/Format.java`

| 行号 | 内容摘要 |
|------|---------|
| 107 | `{@link #colorInfo}` 文档 |
| 186 | `@Nullable private ColorInfo colorInfo;` |
| 281 | `this.colorInfo = format.colorInfo;` |
| 684-691 | `setColorInfo` Builder 方法 |
| 1074 | `@UnstableApi @Nullable public final ColorInfo colorInfo;` |
| 1204, 1345, 1389, 1456, 1538-1539, 1665-1742 | 序列化/反序列化/日志 |

### 7.3 ColorInfo 使用点一览

| 文件 | 关键行号 | 用途 |
|------|---------|------|
| `libraries/extractor/.../MatroskaExtractor.java` | 31, 834, 1251-1266, 2256, 2535-2575 | MKV 解析色彩信息 |
| `libraries/extractor/.../mp4/BoxParser.java` | 29, 1635-2011 | MP4 box 解析色彩信息（av1C/apvC/colr）|
| `libraries/extractor/.../ts/H265Reader.java` | 23, 281-282 | H.265 TS 流色彩信息 |
| `libraries/extractor/.../ts/H264Reader.java` | 24, 242-243 | H.264 TS 流色彩信息 |
| `libraries/container/.../NalUnitUtil.java` | 25, 924-926, 1692-1694, 2382-2385 | NAL 单元色彩信息解析 |
| `libraries/common/.../util/MediaFormatUtil.java` | 25, 101, 161, 266-326 | MediaFormat ↔ ColorInfo 转换 |
| `libraries/common/.../util/CodecSpecificDataUtil.java` | 25, 547-549, 565-575, 901, 929-932 | Codec profile 推断 |
| `libraries/common/.../VideoGraph.java` | 40, 54, 201 | VideoGraph outputColorInfo |
| `libraries/common/.../VideoFrameProcessor.java` | 105, 120, 306 | VideoFrameProcessor outputColorInfo |
| `libraries/common/.../FrameInfo.java` | 29, 44 | FrameInfo 要求 colorInfo 非空 |
| `libraries/exoplayer/.../video/PlaybackVideoGraphWrapper.java` | 39, 563-601, 739-744, 1147, 1257-1364 | tone mapping 输入/输出 ColorInfo 处理 |
| `libraries/exoplayer/.../video/MediaCodecVideoRenderer.java` | 2452, 2521-2524 | MediaFormat 设置 colorInfo |
| `libraries/exoplayer/.../util/DebugTextViewHelper.java` | 24, 145, 196-197 | 调试日志输出 colorInfo |
| `libraries/exoplayer/.../mediacodec/MediaCodecInfo.java` | 49, 460-463 | 解码器复用评估时比较 colorInfo |
| `libraries/exoplayer/.../mediacodec/MediaCodecUtil.java` | 365 | 获取 HEVC profile 时传入 colorInfo |
| `libraries/exoplayer/.../source/mediaparser/OutputConsumerAdapterV30.java` | 475 | MediaParser 输出转 ColorInfo |
| `libraries/exoplayer/.../DecoderReuseEvaluation.java` | 25, 125 | 解码器复用评估原因 |

### 7.4 ColorInfo 测试

| 文件 | 关键行号 |
|------|---------|
| `libraries/common/src/test/.../util/MediaFormatUtilTest.java` | 25, 52, 103-195 |
| `libraries/common/src/test/.../util/CodecSpecificDataUtilTest.java` | 25, 146-177 |
| `libraries/common/src/test/.../FormatTest.java` | 129-164 |
| `libraries/exoplayer/src/test/.../video/PlaybackVideoGraphWrapperTest.java` | 18, 32, 111-216, 394-405 |
| `libraries/exoplayer/src/test/.../mediacodec/MediaCodecInfoTest.java` | 38, 201-524 |
| `libraries/extractor/src/test/.../mp4/Mp4ExtractorParameterizedTest.java` | 146 |

---

## 8. `requestSdrToneMapping`

仅 **1 处** 匹配：

**文件**: `RELEASENOTES.md`

| 行号 | 内容 |
|------|------|
| 2913 | ``TransformationRequest.Builder.setEnableRequestSdrToneMapping(boolean)`` |

> 注：源码中未发现 `requestSdrToneMapping` 标识符。该 API 曾存在于 `TransformationRequest.Builder`（transformer 模块），但本项目中无 transformer 源码。播放器侧实际使用的是 `PlaybackVideoGraphWrapper.setRequestOpenGlToneMapping(boolean)` 和 `setIsInputSdrToneMapped(boolean)`。

---

## 架构总结

### Tone Mapping 数据流

```
ExoPlayer.setVideoEffects()           ← 应用层设置视频效果
  → ExoPlayerImpl.sendRendererMessage(MSG_SET_VIDEO_EFFECTS)
    → MediaCodecVideoRenderer.setVideoEffects()
      → VideoSink.setVideoEffects() / PlaybackVideoGraphWrapper.setVideoEffects()

PlaybackVideoGraphWrapper:
  - requestOpenGlToneMapping: 主动请求 OpenGL tone mapping
  - isInputSdrToneMapped: 输入已被 tone map 到 SDR（跳过 HDR 处理）
  - getAdjustedInputColorInfo(): 若 isInputSdrToneMapped → 返回 SDR_BT709_LIMITED
  - 回退逻辑: HDR color transfer 不被 GL 支持 → 自动 OpenGL tone map 到 SDR
```

### HDR 元数据流

```
容器解析 (BoxParser/MatroskaExtractor/H26xReader)
  → ColorInfo (含 hdrStaticInfo, colorTransfer=HLG/ST2084)
    → Format.colorInfo
      → MediaFormatUtil.maybeSetColorInfo() → MediaCodec (KEY_HDR_STATIC_INFO)
      → MediaCodecVideoRenderer: HDR10+ out-of-band metadata → MediaCodec (PARAMETER_KEY_HDR10_PLUS_INFO)
      → PlaybackVideoGraphWrapper: isTransferHdr() 判断 → 决定 outputColorInfo
```

### 关键文件清单

| 文件 | 角色 |
|------|------|
| `libraries/common/.../ColorInfo.java` | HDR/色彩信息核心数据结构 |
| `libraries/common/.../C.java` | 色彩空间/传输函数常量（HLG/ST2084/SDR）|
| `libraries/exoplayer/.../video/PlaybackVideoGraphWrapper.java` | **Tone mapping 核心**（OpenGL tone map + SDR 回退）|
| `libraries/exoplayer/.../video/MediaCodecVideoRenderer.java` | MediaCodec HDR10+ 元数据 + videoEffects 传递 |
| `libraries/exoplayer/.../ExoPlayer.java` / `ExoPlayerImpl.java` | `setVideoEffects` API |
| `libraries/exoplayer/.../video/VideoSink.java` / `DefaultVideoSink.java` | Video sink effects 接口 |
| `libraries/common/.../util/MediaFormatUtil.java` | MediaFormat ↔ ColorInfo 转换（含 HDR_STATIC_INFO）|
| `libraries/common/.../util/CodecSpecificDataUtil.java` | HDR10 profile 推断 |
| `libraries/extractor/.../mp4/BoxParser.java` | MP4 HDR 元数据解析（clli/mdcv）|
| `libraries/extractor/.../mkv/MatroskaExtractor.java` | MKV HDR 元数据解析 |
| `libraries/common/.../OverlaySettings.java` | HDR overlay 亮度倍率 |
| `libraries/common/.../util/GlUtil.java` | HDR GL 渲染支持 |
