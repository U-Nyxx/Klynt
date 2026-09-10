package com.unyxx.act.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Context Extensions
fun Context.getAppVersion(): String {
    return try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

fun Context.getAppVersionCode(): Int {
    return try {
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
    } catch (e: Exception) {
        1
    }
}

fun Context.isAppInstalled(packageName: String): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

fun Context.getAppIcon(packageName: String): Drawable? {
    return try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationIcon(info)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

fun Context.getAppLabel(packageName: String): String? {
    return try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

fun Context.getAllInstalledPackages(): List<PackageInfo> {
    return packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
}

fun Context.getSharedPrefs(prefsName: String = "klynt_prefs") =
    getSharedPreferences(prefsName, Context.MODE_PRIVATE)

fun Context.getWorldReadablePrefs(prefsName: String = "klynt_prefs") =
    getSharedPreferences(prefsName, Context.MODE_WORLD_READABLE)

fun Context.dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

fun Context.spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity

// View Extensions
fun View.gone() { visibility = View.GONE }
fun View.visible() { visibility = View.VISIBLE }
fun View.invisible() { visibility = View.INVISIBLE }

fun ViewGroup.forEachChild(action: (View) -> Unit) {
    for (i in 0 until childCount) {
        action(getChildAt(i))
    }
}

fun ViewGroup.forEachChildDeep(action: (View) -> Unit) {
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        action(child)
        if (child is ViewGroup) {
            child.forEachChildDeep(action)
        }
    }
}

fun <T : View> View.findChildByClass(clazz: Class<T>): T? {
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (clazz.isInstance(child)) return clazz.cast(child)
            if (child is ViewGroup) {
                val found = child.findChildByClass(clazz)
                if (found != null) return found
            }
        }
    }
    return null
}

fun <T : View> View.findChildrenByClass(clazz: Class<T>): List<T> {
    val results = mutableListOf<T>()
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (clazz.isInstance(child)) results.add(clazz.cast(child))
            if (child is ViewGroup) {
                results.addAll(child.findChildrenByClass(clazz))
            }
        }
    }
    return results
}

// Coroutine Extensions
val supervisedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun <T> MutableStateFlow<T>.update(block: (T) -> T) {
    value = block(value)
}

// String Extensions
fun String.isNullOrBlank(): Boolean = this.isNullOrEmpty() || this.trim().isEmpty()

fun String.capitalizeFirst(): String {
    if (this.isNullOrBlank()) return this
    return this[0].uppercaseChar() + this.substring(1)
}

fun <T : Any> String.parseJsonOrNull(clazz: Class<T>): T? {
    return try {
        com.google.gson.Gson().fromJson(this, clazz)
    } catch (e: Exception) {
        null
    }
}

// File Extensions
fun <T : Any> java.io.File.readJsonOrNull(clazz: Class<T>): T? {
    return try {
        com.google.gson.Gson().fromJson(this.readText(), clazz)
    } catch (e: Exception) {
        null
    }
}

fun java.io.File.writeJson(obj: Any, pretty: Boolean = true) {
    val gson = if (pretty) com.google.gson.GsonBuilder().setPrettyPrinting().create() else com.google.gson.Gson()
    this.writeText(gson.toJson(obj))
}

// Device Extensions
fun isAtLeastAndroidVersion(apiLevel: Int): Boolean = Build.VERSION.SDK_INT >= apiLevel

fun Context.isRooted(): Boolean {
    return try {
        Runtime.getRuntime().exec("su -c id").waitFor() == 0
    } catch (e: Exception) {
        false
    }
}

fun Context.isLSPosedInstalled(): Boolean {
    return isAppInstalled("org.lsposed.manager") ||
           isAppInstalled("io.github.lsposed.manager")
}

fun Context.isXposedInstalled(): Boolean {
    return isAppInstalled("de.robv.android.xposed.installer") ||
           isLSPosedInstalled()
}

// Color Extensions
fun Int.toColorInt(): Int = this

fun Int.withAlpha(alpha: Float): Int {
    val alphaInt = (alpha * 255).toInt()
    return (this and 0x00FFFFFF) or (alphaInt * 16777216)
}