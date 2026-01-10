# 小熊播放器 🐻

一个基于 AndroidX Media3 (ExoPlayer) 的简洁视频播放器应用。

## 项目简介

**小熊播放器** 是一个专注于本地视频播放的Android应用，提供流畅的播放体验和智能的视频管理功能。

### 应用信息
- **应用名称**: 小熊播放器
- **包名**: com.anxiong666.www
- **当前版本**: 3.0.2.6 (versionCode: 3026)
- **最低支持**: Android 7.1 (API 25)
- **目标版本**: Android 14 (API 36)

## 核心功能

### 🎬 视频播放
- 基于ExoPlayer的高性能播放引擎
- 支持多种视频格式
- 流畅的播放控制

### ⭐ 智能管理
- **收藏功能**: 标记喜爱的视频，快速访问
- **待删除队列**: 智能管理待删除视频（可配置1-20个）
- **批量操作**: 支持批量选择和删除

### 📱 用户界面
- 简洁直观的列表展示
- 时间/大小双向排序
- 下拉刷新
- 多选模式支持

## 项目结构

```
codebuddy_media/
├── media/                      # 📱 Android应用代码
│   ├── src/main/
│   │   ├── java/              # Java源代码
│   │   │   └── androidx/media3/demo/media/
│   │   │       ├── MainActivity.java
│   │   │       ├── HomeFragment.java
│   │   │       ├── PlayerActivity.java
│   │   │       ├── SettingsActivity.java
│   │   │       ├── FavoriteManager.java
│   │   │       ├── PendingDeleteManager.java
│   │   │       └── ...
│   │   ├── res/               # 资源文件
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── README.md
│
├── libraries/                  # 📚 ExoPlayer库
│   ├── lib-common/
│   ├── lib-exoplayer/
│   └── lib-ui/
│
├── build.gradle               # 项目构建配置
├── settings.gradle
└── README.md                  # 本文件
```

## 快速开始

### 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 17
- Android SDK API 36
- Gradle 8.0+

### 构建步骤

```bash
# 1. 克隆项目
git clone https://github.com/your-repo/codebuddy_media.git
cd codebuddy_media

# 2. 清理构建缓存
./gradlew clean

# 3. 构建Debug版本
./gradlew :media:assembleDebug

# 4. 安装到设备（需连接设备或启动模拟器）
./gradlew :media:installDebug

# 5. 构建Release版本（需配置签名）
./gradlew :media:assembleRelease
```

### 签名配置

在项目根目录创建 `keystore.properties` 文件：

```properties
storeFile=path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

## 主要依赖

```groovy
// AndroidX核心库
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'

// ExoPlayer - 本地依赖
implementation project(':lib-exoplayer')
implementation project(':lib-ui')
```

## 权限说明

应用需要以下权限：

- **READ_MEDIA_VIDEO** (Android 13+): 读取视频文件
- **READ_EXTERNAL_STORAGE** (Android 12-): 读取外部存储
- **MANAGE_EXTERNAL_STORAGE**: 文件删除操作

## 使用指南

### 基本操作

1. **浏览视频**: 启动应用后自动扫描设备视频
2. **播放视频**: 点击列表中的视频开始播放
3. **收藏视频**: 点击收藏图标标记喜爱的视频
4. **标记待删除**: 长按视频或点击待删除按钮

### 批量操作

1. 长按任意视频进入多选模式
2. 选择需要操作的视频
3. 点击底部操作按钮执行批量删除

### 待删除管理

- 进入设置页面可配置待删除队列大小（1-20个）
- 队列满时自动删除最早标记的文件
- 可随时查看和管理待删除列表

## 进一步优化

查看 [Android项目清理方案.md](Android项目清理方案.md) 了解如何进一步简化代码：

- 移除"更多"Tab和文件浏览器
- 简化HomeFragment功能
- 精简SettingsActivity
- 专注核心视频播放功能

预计可减少约50%的代码量。

## 技术栈

- **UI框架**: Material Design Components
- **视频播放**: AndroidX Media3 (ExoPlayer)
- **架构模式**: Fragment + Activity
- **数据持久化**: SharedPreferences
- **异步处理**: Thread + Handler / ExecutorService

## 版本历史

### v3.0.2.6 (2026-01-03)
- ✅ 待删除功能完善
- ✅ 批量删除支持
- ✅ 收藏管理优化
- ✅ UI界面改进

### v3.0.2.3
- 待删除队列功能
- 播放器界面优化
- 自动切换下一视频

## 开发计划

### 短期计划
- [ ] 添加视频缩略图
- [ ] 优化扫描性能
- [ ] 支持播放历史

### 长期计划
- [ ] 播放速度调节
- [ ] 手势控制音量/亮度
- [ ] 搜索功能
- [ ] 暗黑模式

## 贡献指南

欢迎提交Issue和Pull Request！

## 许可证

基于 [Apache License 2.0](LICENSE) 开源。

## 联系方式

- 项目主页: https://github.com/your-repo/codebuddy_media
- Issue反馈: https://github.com/your-repo/codebuddy_media/issues

---

**基于 AndroidX Media3 开发**  
更多信息请访问 [AndroidX Media 官方文档](https://developer.android.com/guide/topics/media/media3)
