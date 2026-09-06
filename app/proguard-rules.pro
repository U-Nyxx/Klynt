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

-keep class de.robv.android.xposed.** { *; }
-keep class org.lsposed.lsposed.** { *; }
-keep class * extends org.lsposed.lsposed.XposedModule {
    *;
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
-dontwarn de.robv.android.xposed.**
-dontwarn org.lsposed.lsposed.**
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn kotlinx.coroutines.**
