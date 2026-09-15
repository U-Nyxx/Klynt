-dontobfuscate
-dontoptimize
-keepattributes *Annotation*, SourceFile, LineNumberTable

-keep class com.unyxx.act.xposed.KlyntModule { *; }
-keep class com.unyxx.act.xposed.hooks.** { *; }
-keep class com.unyxx.act.xposed.prefs.** { *; }
-keep class com.unyxx.act.xposed.scope.** { *; }
-keep class com.unyxx.act.liquidglass.** { *; }
-keep class com.unyxx.act.manager.** { *; }
-keep class com.unyxx.act.ui.** { *; }
-keep class com.unyxx.act.util.** { *; }
-keep class com.unyxx.act.KlyntApplication { *; }

-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

-keepclasseswithmembernames class * {
    native <methods>;
}

# Compose: rely on the libraries' own consumer rules + compiler-generated rules.
# A blanket androidx.compose keep pins every extended icon (~4000 classes) into
# dex and blows the 20MB diet budget. Our symbols are kept explicitly above.
-dontwarn androidx.compose.**
-keep class androidx.activity.compose.** { *; }
-keep class androidx.lifecycle.compose.** { *; }

# Material3
-keep class com.google.android.material.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# KlyntGlass engine is ours (no third-party glass keeps needed).

-dontwarn android.graphics.RuntimeColorFilter
-dontwarn android.graphics.RuntimeXfermode
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn kotlinx.coroutines.**
