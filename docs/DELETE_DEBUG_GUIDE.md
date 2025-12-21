# 🔧 删除功能调试和优化指南

## 📋 问题概述

在 Android 10+ (API 29+) 系统上，删除媒体文件失败的主要原因：

1. **Scoped Storage 限制** - Android 10 引入了分区存储，应用无法直接删除其他应用创建的文件
2. **播放器占用文件** - ExoPlayer 正在播放时占用文件句柄，导致无法删除
3. **权限不足** - 缺少 `MANAGE_EXTERNAL_STORAGE` 权限
4. **MediaStore 同步延迟** - 删除后文件可能还在缓存中

---

## 🔍 调试步骤

### 步骤1: 查看实时日志

在终端执行以下命令，然后在手机上操作删除按钮：

```bash
# 方式1: 查看删除相关的所有日志
adb logcat | grep -E "(HomeFragment|PlayerActivity|deleteFile|MediaStore)"

# 方式2: 只看错误和删除相关的调试日志
adb logcat *:E PlayerActivity:D HomeFragment:D

# 方式3: 清空日志后重新记录
adb logcat -c && adb logcat PlayerActivity:D HomeFragment:D *:E
```

### 步骤2: 分析日志关键信息

查找以下关键日志：

```
✅ 成功的删除日志：
deleteFileAsync: 文件删除成功
deleteFileUsingMediaStore: MediaStore 删除成功

❌ 失败的关键指标：
deleteFileUsingMediaStore: 文件可写性 - false
deleteFileAsync: 文件删除失败
权限异常: RecoverableSecurityException
文件仍存在
```

### 步骤3: 检查设备信息

```bash
# 查看 Android 版本
adb shell getprop ro.build.version.sdk

# 查看当前应用权限
adb shell dumpsys package androidx.media3.demo.filebrowser | grep permission

# 查看文件详细信息
adb shell ls -la /storage/emulated/0/DCIM/
```

---

## 🛠️ 已实施的优化

### 优化1: 添加权限声明 ✅

在 `AndroidManifest.xml` 中添加了：

```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

### 优化2: 增强日志输出 ✅

在删除函数中添加了详细的日志：
- 文件存在性检查
- 文件可读/可写性检查
- 文件大小
- Android 版本信息
- 每一步操作的结果

### 优化3: 播放器资源释放 ✅

在 `PlayerActivity.deleteCurrentFile()` 中添加：

```java
// 先释放播放器对文件的占用
if (player != null) {
    player.stop();
    player.clearMediaItems();
}

// 增加等待时间到 1 秒
Thread.sleep(1000);
```

### 优化4: RecoverableSecurityException 处理 ✅

添加了用户授权对话框弹出机制：

```java
if (e instanceof android.app.RecoverableSecurityException) {
    startIntentSenderForResult(
        recoverableException.getUserAction().getIntentSender(),
        1001,
        null, 0, 0, 0, null
    );
}
```

---

## 📱 测试方案

### 测试1: 手动授予权限

在设置中授予应用完全的文件访问权限：

```
设置 → 应用 → FileBrowser → 权限 → 文件和媒体 → 允许管理所有文件
```

或通过 ADB 授权：

```bash
adb shell appops set androidx.media3.demo.filebrowser MANAGE_EXTERNAL_STORAGE allow
```

### 测试2: 测试不同场景

1. **列表删除测试** - 在 HomeFragment 中多选删除文件
2. **播放器删除测试** - 在 PlayerActivity 中删除正在播放的文件
3. **不同 Android 版本** - 在 Android 10/11/12+ 上分别测试
4. **不同文件位置**:
   - 应用自己创建的文件（应该能删除）
   - 相机拍摄的视频（可能需要授权）
   - 下载的视频（可能需要授权）

### 测试3: 性能测试

批量删除 10 个文件，观察：
- 删除总耗时
- 成功率
- UI 是否卡顿

---

## 🔧 进一步优化建议

### 建议1: 实现 RecoverableSecurityException 回调

在 `PlayerActivity` 和 `HomeFragment` 中添加：

```java
@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    
    if (requestCode == 1001) {
        if (resultCode == RESULT_OK) {
            // 用户授权成功，重新尝试删除
            Toast.makeText(this, "授权成功，正在删除...", Toast.LENGTH_SHORT).show();
            // 重新调用删除逻辑
        } else {
            Toast.makeText(this, "删除取消", Toast.LENGTH_SHORT).show();
        }
    }
}
```

### 建议2: 使用 SAF (Storage Access Framework)

对于 Android 11+，可以使用 SAF 让用户手动选择要删除的文件：

```java
Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE);
```

### 建议3: 添加删除前确认对话框

```java
new AlertDialog.Builder(this)
    .setTitle("确认删除")
    .setMessage("确定要删除这 " + count + " 个文件吗？")
    .setPositiveButton("删除", (dialog, which) -> {
        deleteSelectedFiles(selectedItems);
    })
    .setNegativeButton("取消", null)
    .show();
```

### 建议4: 优化 MediaStore URI 查询

增加更多查询方式：

```java
// 同时查询多个 MediaStore 表
Uri[] uris = {
    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
};
```

---

## 📊 常见错误及解决方案

### 错误1: SecurityException - Permission Denial

**原因**: 应用没有 `MANAGE_EXTERNAL_STORAGE` 权限

**解决**:
```bash
# 通过 ADB 授权
adb shell appops set androidx.media3.demo.filebrowser MANAGE_EXTERNAL_STORAGE allow

# 或在代码中引导用户手动授权
Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
intent.setData(Uri.parse("package:" + getPackageName()));
startActivity(intent);
```

### 错误2: RecoverableSecurityException

**原因**: 删除其他应用创建的文件需要用户确认

**解决**: 已在代码中实现弹出授权对话框

### 错误3: 文件被占用

**原因**: ExoPlayer 或其他进程正在使用文件

**解决**: 
- 确保调用 `player.stop()` 和 `player.clearMediaItems()`
- 增加延迟等待 `Thread.sleep(1000)`
- 监听播放器状态，确保完全停止后再删除

### 错误4: MediaStore URI 查询失败

**原因**: 
- 文件不在 MediaStore 数据库中
- 使用了 `DATA` 字段但 Android 10+ 限制访问

**解决**: 使用多种查询方式（已优化）

---

## 🎯 验证删除成功的方法

### 方法1: 通过日志验证

```
✅ 看到这些日志表示成功：
deleteFileAsync: 文件删除成功
文件已不存在
```

### 方法2: 通过文件管理器验证

删除后在文件管理器中检查文件是否还存在

### 方法3: 通过 ADB 验证

```bash
# 删除前记录文件
adb shell ls -la /path/to/file

# 删除后检查
adb shell ls -la /path/to/file
# 应该显示 "No such file or directory"
```

### 方法4: 刷新列表验证

在 HomeFragment 中，删除后列表是否自动刷新并移除已删除的文件

---

## 📝 调试清单

使用此清单逐项检查：

- [ ] 已授予 `READ_MEDIA_VIDEO/AUDIO` 权限
- [ ] 已授予 `MANAGE_EXTERNAL_STORAGE` 权限（Android 11+）
- [ ] 播放器已停止并释放资源
- [ ] 等待足够时间（至少 1 秒）
- [ ] 日志显示详细的删除过程
- [ ] 处理了 RecoverableSecurityException
- [ ] MediaStore URI 查询成功
- [ ] 文件确实不存在于磁盘上
- [ ] UI 正确反馈删除结果

---

## 🚀 快速测试命令

复制这些命令到终端直接测试：

```bash
# 1. 清空日志
adb logcat -c

# 2. 开始监听删除日志
adb logcat PlayerActivity:D HomeFragment:D *:E &

# 3. 授予权限（如果需要）
adb shell appops set androidx.media3.demo.filebrowser MANAGE_EXTERNAL_STORAGE allow

# 4. 重启应用
adb shell am force-stop androidx.media3.demo.filebrowser
adb shell am start -n androidx.media3.demo.filebrowser/.MainActivity

# 5. 现在在手机上执行删除操作，观察日志输出
```

---

## 📖 相关文档

- [Android Scoped Storage](https://developer.android.com/about/versions/11/privacy/storage)
- [MediaStore API](https://developer.android.com/reference/android/provider/MediaStore)
- [RecoverableSecurityException](https://developer.android.com/reference/android/app/RecoverableSecurityException)
- [MANAGE_EXTERNAL_STORAGE](https://developer.android.com/reference/android/Manifest.permission#MANAGE_EXTERNAL_STORAGE)

---

**最后更新**: 2025-12-21
