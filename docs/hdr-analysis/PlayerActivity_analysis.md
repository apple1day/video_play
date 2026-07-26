# PlayerActivity 代码分析报告

## 一、文件定位

| 项目 | 路径 / 值 |
|------|-----------|
| PlayerActivity.java | `/Users/houjiaying/code/codebuddy_media/media/src/main/java/androidx/media3/demo/media/PlayerActivity.java` |
| 总行数 | 1106 行 |
| 包名 | `androidx.media3.demo.media` |
| 父类 | `AppCompatActivity` |

---

## 二、initializePlayer() 方法详解（第 595–766 行）

该方法在 `onCreate()` 第 144 行被调用，负责构建并启动 ExoPlayer。

### 2.1 整体流程

```
initializePlayer()
  ├─ 校验 currentFilePath / fileList（空则 finish）
  ├─ 释放旧 player
  ├─ 构建 DefaultRenderersFactory   (608-612)
  ├─ 构建 DefaultLoadControl        (615-622)
  ├─ 构建 TrackSelectionParameters  (629-632)
  ├─ ExoPlayer.Builder().build()    (634-636)
  ├─ player.setTrackSelectionParameters(trackParams)  (639)
  ├─ setVideoScalingMode / setHandleAudioBecomingNoisy / setPlayer  (643-645)
  ├─ 构建 MediaItem 列表（含字幕自动加载）  (648-654)
  ├─ setMediaItems + prepare         (663-664)
  ├─ 标记已观看 / 恢复播放位置 / 恢复播放速度  (667-678)
  ├─ player.play()                   (680)
  └─ 注册 Player.Listener            (682-765)
```

### 2.2 DefaultRenderersFactory（第 608–612 行）

```java
DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    .setEnableDecoderFallback(true);
```

- `EXTENSION_RENDERER_MODE_ON`：启用扩展解码器（如 FFmpeg/IMA），优先硬解
- `setEnableDecoderFallback(true)`：硬解失败时自动 fallback 软解，避免黑屏/花屏

### 2.3 DefaultLoadControl（第 615–622 行）

```java
DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs      */ 15_000,   // 最小缓冲 15s（默认 50s 太激进）
        /* maxBufferMs      */ 50_000,   // 最大缓冲 50s
        /* bufferForPlaybackMs              */ 2_500,  // 首帧等待 2.5s
        /* bufferForPlaybackAfterRebufferMs */ 5_000   // 卡顿后恢复 5s
    )
    .build();
```

注释说明：默认 minBuffer 50s 过于激进，改为 15s 既流畅又省内存。

### 2.4 ⭐ TrackSelectionParameters 配置（第 624–632 行 + 第 639 行）

```java
// ── 轨道选择：强制选最高画质 ──
// ExoPlayer 默认 isViewportSizeLimitedByPhysicalDisplaySize=true，
// 会根据物理屏幕尺寸降选低分辨率轨道（如 4K 降到 1080p）。
// 本地文件播放不需要此限制，清除 viewport 约束确保解码原始全分辨率。
// setForceHighestSupportedBitrate(true) 确保多码率轨道选最高码率。
TrackSelectionParameters trackParams = new TrackSelectionParameters.Builder(this)
    .clearViewportSizeConstraints()          // 禁用屏幕尺寸限制，不降分辨率
    .setForceHighestSupportedBitrate(true)   // 强制选最高码率
    .build();
```

**应用方式（第 638–639 行）：**

```java
// 在 build() 之后设置轨道选择参数（Builder 不直接支持 setTrackSelectionParameters）
player.setTrackSelectionParameters(trackParams);
```

> 注意：`ExoPlayer.Builder` 不直接支持 `setTrackSelectionParameters`，因此在 `build()` 之后通过 `player.setTrackSelectionParameters(trackParams)` 单独设置。

### 2.5 ExoPlayer 构建（第 634–636 行）

```java
player = new ExoPlayer.Builder(this, renderersFactory)
    .setLoadControl(loadControl)
    .build();
```

构建时只传入了 `renderersFactory` 和 `loadControl`，`trackParams` 在 build 后注入。

### 2.6 后续播放器配置（第 643–645 行）

```java
player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);  // 保持宽高比，不拉伸
player.setHandleAudioBecomingNoisy(true);                        // 拔耳机自动暂停
playerView.setPlayer(player);
```

### 2.7 媒体项与播放列表（第 648–664 行）

- 通过 `SubtitleAutoLoader.buildItem(file)` 构建 MediaItem（自动加载同名字幕）
- `player.setMediaItems(mediaItems, currentIndex, 0)` 设置播放列表，从 `currentIndex` 开始
- `player.prepare()` 准备播放

### 2.8 状态恢复与播放控制（第 667–680 行）

- `watchedManager.markAsWatched()`：立即标记为已观看
- `playHistoryManager.getPlayPosition()` → `player.seekTo()`：恢复历史播放位置
- `PlaybackSpeedHelper.restorePlaybackSpeed()`：恢复播放速度
- `player.play()`：开始播放

### 2.9 Player.Listener（第 682–765 行）

注册三个回调：
1. **onPlayerError**：播放错误时自动播放下一个（`playNext()`），失败则 `finish()`
2. **onPlaybackStateChanged**：`STATE_ENDED` 时自动 `seekToNextMediaItem()`，无下一个则 `finish()`
3. **onMediaItemTransition**：切换视频时保存旧视频位置、检查已观看标记、更新 UI、恢复新视频位置、检查是否需要加载更多视频

---

## 三、build.gradle 中 media3 依赖声明

### 3.1 media 模块直接依赖（`media/build.gradle` 第 195–197 行）

```gradle
// Media3 播放器核心库
implementation project(modulePrefix + 'lib-exoplayer')
implementation project(modulePrefix + 'lib-ui')

// 兼容版 MediaSession + MediaStyle 通知（用于锁屏 / 通知栏 / 蓝牙耳机控制）
implementation 'androidx.media:media:' + androidxMediaVersion   // 1.7.0
```

> ⚠️ 注意：`lib-exoplayer` 和 `lib-ui` 是**本地源码模块依赖**（`project(modulePrefix + 'lib-xxx')`），不是 Maven 仓库依赖。`modulePrefix = ':'`（见 constants.gradle 第 71 行）。

### 3.2 lib-exoplayer 传递依赖（`libraries/exoplayer/build.gradle`）

`lib-exoplayer` 通过 `api` 暴露以下模块：

```gradle
api project(modulePrefix + 'lib-common')
api project(modulePrefix + 'lib-datasource')
api project(modulePrefix + 'lib-decoder')
api project(modulePrefix + 'lib-extractor')
api project(modulePrefix + 'lib-database')
```

### 3.3 lib-ui 传递依赖（`libraries/ui/build.gradle`）

```gradle
api project(modulePrefix + 'lib-common')
```

### 3.4 当前使用的 media3 模块汇总

| 模块 | 依赖方式 | 说明 |
|------|----------|------|
| `lib-exoplayer` | 直接 implementation | ExoPlayer 核心 |
| `lib-ui` | 直接 implementation | PlayerView 等 UI 组件 |
| `lib-common` | 传递 api | 公共类（C, MediaItem, Player, TrackSelectionParameters） |
| `lib-datasource` | 传递 api | 数据源 |
| `lib-decoder` | 传递 api | 解码器 |
| `lib-extractor` | 传递 api | 媒体提取器 |
| `lib-database` | 传递 api | 数据库支持 |

### 3.5 media3 版本

| 配置项 | 值 | 来源 |
|--------|-----|------|
| `releaseVersion` | `1.9.0-rc01` | `constants.gradle` 第 15 行 |
| `releaseVersionCode` | `1_009_000_2_01` | `constants.gradle` 第 16 行 |
| `modulePrefix` | `:` | `constants.gradle` 第 71 行 |
| `compileSdkVersion` | `36` | `constants.gradle` 第 24 行 |
| `androidxMediaVersion` | `1.7.0` | `constants.gradle` 第 52 行（兼容版 media，非 media3） |

> **关键结论**：本项目是 **media3 源码工程**（`androidx.media3` 源码树），media3 模块以本地 `project()` 方式依赖，版本为 `1.9.0-rc01`。`androidx.media:media:1.7.0` 是旧版兼容 MediaSession 库，用于锁屏/通知栏控制，与 media3 并存。

---

## 四、PlayerActivity 关键导入（media3 相关）

```java
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
```

均在 `lib-common`（C, MediaItem, Player, TrackSelectionParameters）、`lib-exoplayer`（DefaultLoadControl, DefaultRenderersFactory, ExoPlayer）、`lib-ui`（PlayerView）三个模块中定义。

---

## 五、设计要点小结

1. **TrackSelectionParameters 的核心意图**：清除 viewport 屏幕尺寸约束 + 强制最高码率，确保本地 4K/高码率视频不被降级到 1080p。
2. **参数注入时机**：因 `ExoPlayer.Builder` 不直接支持 `setTrackSelectionParameters`，采用 build 后 `player.setTrackSelectionParameters()` 的方式注入。
3. **缓冲策略**：minBuffer 从默认 50s 降至 15s，平衡流畅度与内存。
4. **解码器策略**：硬解优先 + 软解 fallback（`EXTENSION_RENDERER_MODE_ON` + `setEnableDecoderFallback(true)`）。
5. **依赖模型**：media3 为本地源码依赖（非 Maven），版本 `1.9.0-rc01`，共引入 7 个子模块。
