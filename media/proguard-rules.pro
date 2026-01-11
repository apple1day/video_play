# ================================
# 视频播放器 ProGuard 规则
# 版本: 3.1.0
# ================================

# 基础混淆配置
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# 保留行号信息（便于调试崩溃日志）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留注解
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ================================
# AndroidX 和 Support 库
# ================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# RecyclerView
-keep class androidx.recyclerview.widget.** { *; }

# ViewPager2
-keep class androidx.viewpager2.widget.** { *; }

# ================================
# Media3 / ExoPlayer
# ================================
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ExoPlayer 核心
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ================================
# Glide 图片加载库
# ================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# ================================
# 应用自定义类
# ================================
# 保留所有 Activity
-keep public class * extends android.app.Activity
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment

# 保留所有 Application
-keep public class * extends android.app.Application

# 保留所有 Adapter
-keep class * extends androidx.recyclerview.widget.RecyclerView$Adapter {
    <init>(...);
    public <methods>;
}

# 保留所有 ViewHolder
-keep class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    <init>(...);
}

# 保留自定义 View
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(***);
    *** get*();
}

# ================================
# 文件管理相关
# ================================
# FileProvider
-keep class androidx.core.content.FileProvider { *; }

# DocumentsProvider
-keep class * extends android.provider.DocumentsProvider {
    <init>(...);
    public <methods>;
}

# 自定义 Provider
-keep class androidx.media3.demo.media.DocumentsProvider { *; }

# ================================
# 数据模型类
# ================================
-keep class androidx.media3.demo.media.FileItem { *; }
-keep class androidx.media3.demo.media.FileItem$** { *; }

# 管理器类
-keep class androidx.media3.demo.media.FavoriteManager { *; }
-keep class androidx.media3.demo.media.PendingDeleteManager { *; }
-keep class androidx.media3.demo.media.PermissionManager { *; }
-keep class androidx.media3.demo.media.PermissionHelper { *; }

# 工具类
-keep class androidx.media3.demo.media.FileUtils { *; }
-keep class androidx.media3.demo.media.FileShareHelper { *; }

# ================================
# 反射相关
# ================================
# 保留所有使用反射的类
-keepclassmembers class * {
    public void *(**On*Event);
    public void *(**On*Listener);
}

# ================================
# 序列化相关
# ================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ================================
# Native 方法
# ================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ================================
# Enum 枚举类
# ================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ================================
# JavaScript 接口（WebView）
# ================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ================================
# 日志优化（Release 版本移除日志）
# ================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# ================================
# 系统组件
# ================================
-keep class * extends java.lang.annotation.Annotation { *; }
-keep interface * extends java.lang.annotation.Annotation { *; }

# ================================
# Kotlin 相关
# ================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ================================
# 第三方库警告忽略
# ================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ================================
# 移除无用资源和代码
# ================================
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
}

# ================================
# 优化选项
# ================================
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-allowaccessmodification
-repackageclasses ''
