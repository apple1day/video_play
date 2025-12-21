# ✅ 删除功能测试指南

## 🎉 编译成功！已安装应用

应用已成功安装到设备上，现在可以开始测试删除功能。

---

## 🚀 快速测试步骤

### 步骤 1: 授予权限

在终端执行：

```bash
cd /Users/houjiaying/code/codebuddy_media/demos
./debug_delete.sh -p
```

或者手动授权：
```
设置 → 应用 → FileBrowser → 权限 → 文件和媒体 → 允许管理所有文件
```

### 步骤 2: 启动日志监控

在新终端窗口执行：

```bash
cd /Users/houjiaying/code/codebuddy_media/demos
./debug_delete.sh -l
```

保持这个终端窗口打开，它会实时显示删除日志。

### 步骤 3: 重启应用

```bash
cd /Users/houjiaying/code/codebuddy_media/demos
./debug_delete.sh -r
```

### 步骤 4: 测试删除功能

#### **测试 A: 列表多选删除**

1. 打开应用，进入主页
2. **长按**任意视频文件，进入多选模式
3. 选择 1-3 个文件
4. 点击顶部的**"删除选中"**按钮
5. 观察终端日志输出

**预期结果**：
- ✅ Toast 提示 "删除成功"
- ✅ 文件从列表中消失
- ✅ 日志显示 "文件删除成功"

#### **测试 B: 播放器删除**

1. 点击任意视频进入播放界面
2. 点击左上角的**删除按钮**（垃圾桶图标）
3. 观察终端日志输出

**预期结果**：
- ✅ Toast 提示 "文件删除成功"
- ✅ 自动播放下一个视频（如果有）
- ✅ 日志显示完整的删除过程

---

## 📊 日志分析

### ✅ 成功的日志示例

```
D/PlayerActivity: deleteCurrentFile: 准备删除文件 - /storage/emulated/0/DCIM/test.mp4
D/PlayerActivity: deleteCurrentFile: 文件存在 - true
D/PlayerActivity: deleteCurrentFile: 文件可读 - true
D/PlayerActivity: deleteCurrentFile: 文件可写 - true
D/PlayerActivity: deleteCurrentFile: 释放播放器资源
D/PlayerActivity: deleteFileAsync: 进入删除任务执行体
D/PlayerActivity: deleteFileAsync: 开始异步删除文件
D/PlayerActivity: deleteFileAsync: 文件可写性 - true
D/PlayerActivity: deleteFileUsingMediaStore: 找到 MediaStore URI - content://media/external/video/media/123
D/PlayerActivity: deleteFileUsingMediaStore: MediaStore 删除结果 - 1
D/PlayerActivity: deleteFileUsingMediaStore: MediaStore 删除成功
D/PlayerActivity: deleteFileAsync: 文件删除成功
```

### ❌ 失败的日志示例

#### 情况1: 权限不足

```
E/PlayerActivity: deleteFileUsingMediaStore: 权限异常 - RecoverableSecurityException
D/PlayerActivity: deleteFileUsingMediaStore: 请求用户授权删除
```

**解决方案**：
- 应该会弹出系统对话框请求授权
- 点击"允许"即可
- 如果没有弹出，执行 `./debug_delete.sh -p` 授权

#### 情况2: 文件被占用

```
D/PlayerActivity: deleteFileAsync: 文件可写性 - false
E/PlayerActivity: deleteFileAsync: 文件删除失败
```

**解决方案**：
- 已优化：增加了播放器释放逻辑
- 如果仍失败，增加等待时间到 2 秒

#### 情况3: MediaStore 找不到文件

```
W/PlayerActivity: deleteFileUsingMediaStore: 无法获取 MediaStore URI
D/PlayerActivity: deleteFileUsingMediaStore: 尝试直接删除
```

**说明**：
- 文件可能不在 MediaStore 数据库中
- 会自动回退到直接删除
- 如果直接删除成功，这不是问题

---

## 🔧 常见问题排查

### 问题1: 删除后文件仍存在

**检查步骤**：

1. 查看日志中的删除结果
   ```bash
   adb logcat | grep "删除成功\|删除失败"
   ```

2. 检查文件是否真的存在
   ```bash
   adb shell ls -la /storage/emulated/0/DCIM/
   ```

3. 刷新文件列表
   - 退出应用重新进入
   - 或下拉刷新（如果实现了）

### 问题2: Toast 显示"删除失败"

**排查步骤**：

1. 查看完整日志找到具体错误
   ```bash
   ./debug_delete.sh -l
   ```

2. 检查权限状态
   ```bash
   ./debug_delete.sh -i
   ```

3. 重新授权
   ```bash
   ./debug_delete.sh -p
   ```

### 问题3: 应用崩溃

**排查步骤**：

1. 查看崩溃日志
   ```bash
   adb logcat | grep -i "fatal\|exception"
   ```

2. 检查是否是空指针
   - 代码中已添加大量 null 检查
   - 如果仍崩溃，提供日志

### 问题4: 删除很慢

**正常情况**：
- 删除前有 1 秒等待时间（确保播放器释放）
- 这是正常的优化措施

**异常情况**：
- 如果超过 3 秒，查看日志是否有重试
- 可能是 MediaStore 查询慢

---

## 📱 测试清单

### 基础功能测试

- [ ] 删除单个文件（列表）
- [ ] 删除多个文件（列表）
- [ ] 删除正在播放的文件（播放器）
- [ ] 删除后列表自动刷新
- [ ] Toast 正确显示结果

### 边界情况测试

- [ ] 删除最后一个文件
- [ ] 删除不存在的文件
- [ ] 删除正在被其他应用占用的文件
- [ ] 删除只读文件
- [ ] 删除系统文件（应该失败）

### 权限测试

- [ ] 有 MANAGE_EXTERNAL_STORAGE 权限
- [ ] 没有 MANAGE_EXTERNAL_STORAGE 权限（应弹出授权对话框）
- [ ] 拒绝授权后的行为

### 性能测试

- [ ] 删除 1 个文件的耗时
- [ ] 删除 10 个文件的耗时
- [ ] 删除过程中 UI 不卡顿

### Android 版本测试

- [ ] Android 10 (API 29)
- [ ] Android 11 (API 30)
- [ ] Android 12 (API 31)
- [ ] Android 13+ (API 33+)

---

## 🎯 优化后的改进点

### 已修复的问题

1. ✅ **播放器文件占用** - 删除前释放播放器资源
2. ✅ **权限不足** - 添加 RecoverableSecurityException 处理
3. ✅ **日志不足** - 添加详细的调试日志
4. ✅ **编译错误** - 修复 PendingIntent.getIntentSender() 调用

### 核心改进

#### PlayerActivity.deleteCurrentFile()
```java
// 🔑 关键改进：删除前释放播放器
if (player != null) {
    player.stop();
    player.clearMediaItems();
}

// 增加等待时间到 1 秒
Thread.sleep(1000);
```

#### HomeFragment & PlayerActivity - deleteFileUsingMediaStore()
```java
// 🔑 关键改进：处理用户授权
catch (SecurityException e) {
    if (e instanceof RecoverableSecurityException) {
        // 弹出系统授权对话框
        PendingIntent pendingIntent = 
            recoverableException.getUserAction().getActionIntent();
        startIntentSenderForResult(
            pendingIntent.getIntentSender(),
            1001, null, 0, 0, 0, null
        );
    }
}
```

---

## 📚 相关命令速查

```bash
# 快速测试（推荐）
./debug_delete.sh -t

# 授予权限
./debug_delete.sh -p

# 查看日志
./debug_delete.sh -l

# 查看设备信息
./debug_delete.sh -i

# 重启应用
./debug_delete.sh -r

# 检查文件
./debug_delete.sh -f

# 帮助信息
./debug_delete.sh -h

# 手动安装 APK
adb install -r demos/filebrowser/buildout/outputs/apk/debug/demo-filebrowser-debug.apk

# 手动授权（备用）
adb shell appops set androidx.media3.demo.filebrowser MANAGE_EXTERNAL_STORAGE allow

# 查看实时日志（备用）
adb logcat PlayerActivity:D HomeFragment:D *:E

# 清空日志
adb logcat -c
```

---

## 🎓 技术说明

### RecoverableSecurityException 处理流程

1. **应用尝试删除文件** → MediaStore.delete()
2. **系统检测到无权限** → 抛出 RecoverableSecurityException
3. **应用捕获异常** → 获取 PendingIntent
4. **启动授权对话框** → startIntentSenderForResult()
5. **用户点击允许** → onActivityResult(RESULT_OK)
6. **重新尝试删除** → 成功

### Android 10+ Scoped Storage 限制

| 文件来源 | 是否需要授权 | 删除方式 |
|---------|------------|---------|
| 应用自己创建 | ❌ 不需要 | 直接删除 |
| 相机拍摄 | ✅ 需要 | 用户确认 |
| 其他应用创建 | ✅ 需要 | 用户确认 |
| 下载的文件 | ✅ 需要 | 用户确认 |

---

## 🎬 现在开始测试吧！

1. 打开终端，执行快速测试：
   ```bash
   cd /Users/houjiaying/code/codebuddy_media/demos
   ./debug_delete.sh -t
   ```

2. 在手机上打开应用

3. 尝试删除文件，观察日志输出

4. 如果遇到问题，查看本文档的"常见问题排查"部分

---

**祝测试顺利！** 🚀

有任何问题，请提供完整的日志输出。
