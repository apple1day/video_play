# File Browser Demo

这是一个文件浏览器和媒体播放器示例应用，展示了如何使用 AndroidX Media3 (ExoPlayer) 进行视频播放。

## 功能特性

1. **双 Tab 界面**
   - 首页：显示所有媒体文件
   - 更多：文件浏览器和存储信息

2. **首页功能**
   - 扫描本地存储和外置存储的所有文件
   - 支持按类型筛选（视频/音乐/文档/全部）
   - 支持按时间或大小排序
   - 点击视频文件可播放

3. **更多 Tab 功能**
   - 浏览本地存储和外置存储目录
   - 显示存储空间信息（总空间、已用空间、可用空间）
   - 文件夹-文件模式浏览

4. **视频播放功能**
   - 使用 ExoPlayer 播放视频
   - 左侧删除按钮
   - 删除后自动播放下一个视频
   - 如果没有下一个视频，自动关闭播放器

## 权限要求

- Android 13+ (API 33+): READ_MEDIA_VIDEO, READ_MEDIA_AUDIO
- Android 10-12 (API 29-32): READ_EXTERNAL_STORAGE
- Android 9 及以下: READ_EXTERNAL_STORAGE

## 使用方法

1. 运行应用
2. 授予存储权限
3. 在首页浏览和筛选文件
4. 点击视频文件进行播放
5. 在播放界面可以删除当前视频

## 技术栈

- AndroidX Media3 (ExoPlayer)
- Material Design Components
- ViewPager2 + TabLayout
- RecyclerView

