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

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep class androidx.activity.compose.** { *; }
-keep class androidx.lifecycle.compose.** { *; }
-keep class androidx.navigation.compose.** { *; }

# Material3
-keep class com.google.android.material.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Liquid Glass
-keep class com.example.liquidglass.** { *; }

-dontwarn android.graphics.RuntimeColorFilter
-dontwarn android.graphics.RuntimeXfermode
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn kotlinx.coroutines.**
