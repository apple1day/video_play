# 项目清理删除文档

## 文档说明

本文档详细记录了在清理项目过程中删除的所有文件夹和目录，仅保留 filebrowser 应用及其必要的依赖库。

**清理日期**: 2025年12月13日  
**清理目的**: 删除与 filebrowser 无关的代码，保留必要的依赖文件  
**保留应用**: `demos/filebrowser/` - 文件浏览器和媒体播放器应用

---

## 一、测试应用目录（完全删除）

### 1. testapps/ 目录
- **路径**: `testapps/` ok
- **说明**: 整个测试应用目录
- **包含内容**:
    - `testapps/controller/` - MediaController 测试应用
        - 源代码文件（Kotlin）
        - 资源文件（布局、图片、字符串等）
        - 构建配置文件
        - 构建输出文件

### 2. docsamples/ 目录
- **路径**: `docsamples/`
- **说明**: 文档示例代码目录
- **包含内容**: 文档相关的示例代码

---

## 二、Demo 应用目录（除 filebrowser 外全部删除）

### 3. demos/cast/
- **路径**: `demos/cast/`
- **说明**: Google Cast 演示应用
- **包含内容**: Cast 功能演示的源代码和资源

### 4. demos/compose/
- **路径**: `demos/compose/`
- **说明**: Jetpack Compose 演示应用
- **包含内容**: Kotlin Compose UI 代码、资源文件

### 5. demos/composition/
- **路径**: `demos/composition/` ok
- **说明**: 视频合成演示应用
- **包含内容**: 视频合成功能的演示代码

### 6. demos/effect/
- **路径**: `demos/effect/`
- **说明**: 视频效果处理演示应用
- **包含内容**: 视频效果处理的演示代码

### 7. demos/gl/
- **路径**: `demos/gl/`
- **说明**: OpenGL 演示应用
- **包含内容**: OpenGL 相关演示代码

### 8. demos/main/
- **路径**: `demos/main/`
- **说明**: 主演示应用
- **包含内容**: 主要的 ExoPlayer 演示应用代码

### 9. demos/session/
- **路径**: `demos/session/`
- **说明**: MediaSession 演示应用
- **包含内容**: MediaSession 功能的演示代码

### 10. demos/session_automotive/
- **路径**: `demos/session_automotive/`
- **说明**: Automotive Session 演示应用
- **包含内容**: 车载系统 MediaSession 演示代码

### 11. demos/session_service/
- **路径**: `demos/session_service/`
- **说明**: Session Service 演示应用
- **包含内容**: MediaSessionService 演示代码

### 12. demos/shortform/
- **路径**: `demos/shortform/`
- **说明**: Shortform 演示应用
- **包含内容**: 短视频格式演示代码

### 13. demos/surface/
- **路径**: `demos/surface/`
- **说明**: Surface 演示应用
- **包含内容**: Surface 相关演示代码

### 14. demos/transformer/
- **路径**: `demos/transformer/`
- **说明**: Transformer 演示应用
- **包含内容**: 媒体转换演示代码

---

## 三、库模块目录（与 filebrowser 无关的全部删除）

### 3.1 扩展 ExoPlayer 库

#### 15. libraries/exoplayer_dash/
- **路径**: `libraries/exoplayer_dash/`
- **说明**: DASH 流媒体支持库
- **功能**: 支持 DASH (Dynamic Adaptive Streaming over HTTP) 流媒体协议

#### 16. libraries/exoplayer_hls/
- **路径**: `libraries/exoplayer_hls/`
- **说明**: HLS 流媒体支持库
- **功能**: 支持 HLS (HTTP Live Streaming) 流媒体协议

#### 17. libraries/exoplayer_rtsp/
- **路径**: `libraries/exoplayer_rtsp/` ok
- **说明**: RTSP 流媒体支持库
- **功能**: 支持 RTSP (Real Time Streaming Protocol) 流媒体协议

#### 18. libraries/exoplayer_smoothstreaming/
- **路径**: `libraries/exoplayer_smoothstreaming/`
- **说明**: SmoothStreaming 支持库
- **功能**: 支持 Microsoft SmoothStreaming 流媒体协议

#### 19. libraries/exoplayer_ima/
- **路径**: `libraries/exoplayer_ima/`
- **说明**: IMA 广告支持库
- **功能**: 支持 Google IMA (Interactive Media Ads) 广告集成

#### 20. libraries/exoplayer_workmanager/
- **路径**: `libraries/exoplayer_workmanager/`
- **说明**: WorkManager 集成库
- **功能**: ExoPlayer 与 WorkManager 的集成支持

### 3.2 UI 扩展库

#### 21. libraries/ui_compose/
- **路径**: `libraries/ui_compose/`
- **说明**: Jetpack Compose UI 库
- **功能**: 为 ExoPlayer 提供 Compose UI 组件

#### 22. libraries/ui_compose_material3/
- **路径**: `libraries/ui_compose_material3/`
- **说明**: Material3 Compose UI 库
- **功能**: Material Design 3 风格的 Compose UI 组件

#### 23. libraries/ui_leanback/
- **路径**: `libraries/ui_leanback/`
- **说明**: Leanback UI 库
- **功能**: Android TV Leanback UI 组件

### 3.3 数据源扩展库

#### 24. libraries/datasource_cronet/
- **路径**: `libraries/datasource_cronet/`
- **说明**: Cronet 数据源库
- **功能**: 使用 Chromium 的 Cronet 网络库作为数据源

#### 25. libraries/datasource_okhttp/
- **路径**: `libraries/datasource_okhttp/`
- **说明**: OkHttp 数据源库
- **功能**: 使用 OkHttp 网络库作为数据源

#### 26. libraries/datasource_rtmp/
- **路径**: `libraries/datasource_rtmp/`
- **说明**: RTMP 数据源库
- **功能**: 支持 RTMP (Real-Time Messaging Protocol) 数据源

### 3.4 解码器扩展库

#### 27. libraries/decoder_av1/
- **路径**: `libraries/decoder_av1/`
- **说明**: AV1 解码器库
- **功能**: AV1 视频解码支持

#### 28. libraries/decoder_ffmpeg/
- **路径**: `libraries/decoder_ffmpeg/`
- **说明**: FFmpeg 解码器库
- **功能**: 使用 FFmpeg 进行视频解码

#### 29. libraries/decoder_flac/
- **路径**: `libraries/decoder_flac/`
- **说明**: FLAC 解码器库
- **功能**: FLAC 音频解码支持

#### 30. libraries/decoder_iamf/
- **路径**: `libraries/decoder_iamf/`
- **说明**: IAMF 解码器库
- **功能**: IAMF (Immersive Audio Model and Format) 解码支持

#### 31. libraries/decoder_midi/
- **路径**: `libraries/decoder_midi/`
- **说明**: MIDI 解码器库
- **功能**: MIDI 音频解码支持

#### 32. libraries/decoder_mpegh/
- **路径**: `libraries/decoder_mpegh/`
- **说明**: MPEG-H 解码器库
- **功能**: MPEG-H 音频解码支持

#### 33. libraries/decoder_opus/
- **路径**: `libraries/decoder_opus/`
- **说明**: Opus 解码器库
- **功能**: Opus 音频解码支持

#### 34. libraries/decoder_vp9/
- **路径**: `libraries/decoder_vp9/`
- **说明**: VP9 解码器库
- **功能**: VP9 视频解码支持

### 3.5 其他功能库

#### 35. libraries/cast/
- **路径**: `libraries/cast/`
- **说明**: Google Cast 支持库
- **功能**: Google Cast 功能集成

#### 36. libraries/session/
- **路径**: `libraries/session/`
- **说明**: MediaSession 库
- **功能**: Android MediaSession API 支持
- **注意**: 部分构建输出目录可能仍有残留

#### 37. libraries/effect/
- **路径**: `libraries/effect/`
- **说明**: 视频效果处理库
- **功能**: 视频效果和滤镜处理

#### 38. libraries/inspector/
- **路径**: `libraries/inspector/`
- **说明**: 媒体检查器库
- **功能**: 媒体文件检查和调试工具

#### 39. libraries/muxer/
- **路径**: `libraries/muxer/`
- **说明**: 媒体混合器库
- **功能**: 媒体文件混合和封装

#### 40. libraries/transformer/
- **路径**: `libraries/transformer/`
- **说明**: 媒体转换库
- **功能**: 媒体格式转换和处理

#### 41. libraries/common_ktx/
- **路径**: `libraries/common_ktx/`
- **说明**: Kotlin 扩展库
- **功能**: 为 common 库提供 Kotlin 扩展函数

### 3.6 测试相关库

#### 42. libraries/test_utils/
- **路径**: `libraries/test_utils/`
- **说明**: 测试工具库
- **功能**: 提供测试工具类和辅助方法

#### 43. libraries/test_utils_robolectric/
- **路径**: `libraries/test_utils_robolectric/`
- **说明**: Robolectric 测试工具库
- **功能**: 为 Robolectric 测试提供工具类

#### 44. libraries/test_data/
- **路径**: `libraries/test_data/`
- **说明**: 测试数据库
- **功能**: 包含测试用的媒体文件和测试数据

#### 45. libraries/test_exoplayer_playback/
- **路径**: `libraries/test_exoplayer_playback/`
- **说明**: ExoPlayer 播放测试库
- **功能**: ExoPlayer 播放功能的测试工具

#### 46. libraries/test_proguard/
- **路径**: `libraries/test_proguard/`
- **说明**: ProGuard 测试库
- **功能**: ProGuard 混淆测试工具

#### 47. libraries/test_session_common/
- **路径**: `libraries/test_session_common/`
- **说明**: Session 通用测试库
- **功能**: MediaSession 通用测试工具

#### 48. libraries/test_session_current/
- **路径**: `libraries/test_session_current/`
- **说明**: Session 当前版本测试库
- **功能**: MediaSession 当前版本的测试工具

---

## 四、文档和脚本文件（部分删除）

### 49. anxiong/src/
- **路径**: `anxiong/src/`
- **说明**: 另一个项目的源代码目录
- **包含内容**:
    - MainActivity.java
    - PlayerActivity.java
    - VideoItem.java
    - 布局文件等

### 50. anxiong/build.gradle
- **路径**: `anxiong/build.gradle`
- **说明**: 另一个项目的构建配置文件

### 51. anxiong/*.ps1
- **路径**: `anxiong/` 目录下的 PowerShell 脚本
- **包含文件**:
    - `adb-helper.ps1` - ADB 辅助脚本
    - `fix-adb-server.ps1` - ADB 服务器修复脚本
    - `快速修复ADB.ps1` - 快速修复 ADB 脚本
    - `添加ADB到PATH.ps1` - ADB 路径配置脚本

---

## 删除统计

### 按类别统计

| 类别 | 数量 | 说明 |
|------|------|------|
| 测试应用目录 | 2 | testapps/, docsamples/ |
| Demo 应用目录 | 11 | 除 filebrowser 外的所有 demo |
| 扩展 ExoPlayer 库 | 6 | DASH, HLS, RTSP, SmoothStreaming, IMA, WorkManager |
| UI 扩展库 | 3 | Compose, Compose Material3, Leanback |
| 数据源扩展库 | 3 | Cronet, OkHttp, RTMP |
| 解码器扩展库 | 8 | AV1, FFmpeg, FLAC, IAMF, MIDI, MPEG-H, Opus, VP9 |
| 其他功能库 | 7 | Cast, Session, Effect, Inspector, Muxer, Transformer, Common KTX |
| 测试相关库 | 6 | 各种测试工具库 |
| 文档/脚本文件 | 部分 | anxiong 目录下的部分文件 |

### 总计
- **主要目录**: 约 47 个
- **删除的文件**: 数千个（包括源代码、资源文件、构建输出等）

---

## 保留的内容

### 保留的 Demo 应用

#### demos/filebrowser/
- **路径**: `demos/filebrowser/`
- **说明**: 文件浏览器和媒体播放器应用
- **功能**:
    - 扫描和浏览本地媒体文件
    - 视频播放功能
    - 文件管理功能
- **包含内容**:
    - 源代码（Java）
    - 资源文件（布局、图片、字符串）
    - 构建配置
    - 文档文件

### 保留的库模块（filebrowser 必需）

#### 1. libraries/common/
- **说明**: 核心通用库
- **功能**: 提供 Media3 的核心功能和工具类
- **依赖**: 无（基础库）

#### 2. libraries/container/
- **说明**: 容器库
- **功能**: 媒体容器格式支持
- **依赖**: lib-common

#### 3. libraries/database/
- **说明**: 数据库库
- **功能**: 媒体数据库支持
- **依赖**: lib-common

#### 4. libraries/datasource/
- **说明**: 数据源库
- **功能**: 媒体数据源抽象和实现
- **依赖**: lib-common, lib-database

#### 5. libraries/decoder/
- **说明**: 基础解码器库
- **功能**: 媒体解码器抽象和基础实现
- **依赖**: lib-common

#### 6. libraries/extractor/
- **说明**: 媒体提取器库
- **功能**: 从容器中提取媒体数据
- **依赖**: lib-common, lib-container, lib-decoder

#### 7. libraries/exoplayer/
- **说明**: ExoPlayer 核心库
- **功能**: 媒体播放核心功能
- **依赖**: lib-common, lib-container, lib-datasource, lib-decoder, lib-extractor, lib-database

#### 8. libraries/ui/
- **说明**: UI 库
- **功能**: ExoPlayer 的 UI 组件（PlayerView 等）
- **依赖**: lib-common

---

## 依赖关系图

```
filebrowser (应用)
    ├── lib-exoplayer
    │   ├── lib-common
    │   ├── lib-container
    │   │   └── lib-common
    │   ├── lib-datasource
    │   │   ├── lib-common
    │   │   └── lib-database
    │   │       └── lib-common
    │   ├── lib-decoder
    │   │   └── lib-common
    │   ├── lib-extractor
    │   │   ├── lib-common
    │   │   ├── lib-container
    │   │   └── lib-decoder
    │   └── lib-database
    └── lib-ui
        └── lib-common
```

---

## 清理后的项目结构

```
v5_media/
├── demos/
│   └── filebrowser/          # 唯一保留的 demo 应用
├── libraries/
│   ├── common/              # 核心库
│   ├── container/            # 容器库
│   ├── database/            # 数据库库
│   ├── datasource/          # 数据源库
│   ├── decoder/             # 解码器库
│   ├── extractor/           # 提取器库
│   ├── exoplayer/            # ExoPlayer 核心库
│   └── ui/                   # UI 库
├── build.gradle
├── settings.gradle
├── core_settings.gradle
└── ... (其他配置文件)
```

---

## 注意事项

1. **构建输出目录**: 部分 `buildout/` 目录可能仍有残留，这些是构建缓存，可以安全删除
2. **Git 历史**: 如果使用 Git，删除的文件仍在 Git 历史中，可以通过 `git log` 查看
3. **恢复方法**: 如果需要恢复已删除的文件，可以从 Git 历史中恢复（如果已提交）
4. **依赖完整性**: 保留的库模块已确保 filebrowser 应用可以正常构建和运行

---

## 清理效果

- **项目大小**: 显著减小（删除了大量不必要的代码和资源）
- **构建速度**: 提升（减少了需要编译的模块）
- **维护成本**: 降低（只需维护 filebrowser 应用）
- **代码清晰度**: 提升（只保留相关代码）

---

## 后续建议

1. **定期清理**: 定期清理构建输出目录（`buildout/`）
2. **版本控制**: 确保重要更改已提交到版本控制系统
3. **文档更新**: 更新项目 README，说明当前项目结构
4. **依赖检查**: 定期检查依赖库的更新和安全补丁

---

**文档生成时间**: 2025年12月13日  
**文档版本**: 1.0

