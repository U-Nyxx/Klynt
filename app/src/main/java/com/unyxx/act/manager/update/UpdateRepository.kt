package com.unyxx.act.manager.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.unyxx.act.BuildConfig
import com.unyxx.act.network.GitHubApi
import com.unyxx.act.network.ReleaseInfo
import com.unyxx.act.xposed.prefs.PrefsSchema
import java.io.File

/** UI states for the update flow. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val progress: Float?) : UpdateState
    data class Downloaded(val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * GitHub-release update source of truth.
 *
 * Check results are cached 24h in manager prefs to respect rate limits.
 * Downloads go through the system DownloadManager; installs via our
 * FileProvider (already mapped to Download/).
 */
object UpdateRepository {

    private const val CACHE_VALID_MS = 24L * 60L * 60L * 1000L
    private const val KEY_CHECK_MS = "update_last_check_ms"
    private const val KEY_TAG = "update_cached_tag"
    private const val KEY_NOTES = "update_cached_notes"
    private const val KEY_URL = "update_cached_url"
    private const val KEY_SIZE = "update_cached_size"
    private const val APK_FILE = "klynt-update.apk"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PrefsSchema.PREFS_FILE, Context.MODE_PRIVATE)

    fun localVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }

    /** Epoch ms of the last check (cache stamp), 0 when never checked. */
    fun lastCheckMs(context: Context): Long {
        return try {
            prefs(context).getLong(KEY_CHECK_MS, 0L)
        } catch (_: Throwable) {
            0L
        }
    }

    /** Returns cached-or-fresh update state. Throws nothing. */
    suspend fun check(context: Context, force: Boolean): UpdateState {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (!force) {
            val cachedTag = p.getString(KEY_TAG, null)
            if (cachedTag != null && now - p.getLong(KEY_CHECK_MS, 0L) < CACHE_VALID_MS) {
                val info = ReleaseInfo(
                    version = cachedTag,
                    notes = p.getString(KEY_NOTES, "").orEmpty(),
                    apkUrl = p.getString(KEY_URL, "").orEmpty(),
                    sizeBytes = p.getLong(KEY_SIZE, 0L)
                )
                return if (info.apkUrl.isBlank()) {
                    UpdateState.UpToDate(localVersion(context))
                } else if (info.isNewerThan(localVersion(context))) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate(localVersion(context))
                }
            }
        }
        return try {
            val release = GitHubApi.fetchLatest()
            val info = ReleaseInfo.from(release)
                ?: return UpdateState.Failed("No APK in latest release")
            p.edit()
                .putLong(KEY_CHECK_MS, now)
                .putString(KEY_TAG, info.version)
                .putString(KEY_NOTES, info.notes)
                .putString(KEY_URL, info.apkUrl)
                .putLong(KEY_SIZE, info.sizeBytes)
                .apply()
            if (info.isNewerThan(localVersion(context))) {
                UpdateState.Available(info)
            } else {
                UpdateState.UpToDate(info.version)
            }
        } catch (t: Throwable) {
            UpdateState.Failed(t.message ?: "Network error")
        }
    }

    /** Enqueues the APK download, returns the DownloadManager id. */
    fun enqueueDownload(context: Context, info: ReleaseInfo): Long {
        val file = updateFile(context)
        if (file.exists()) file.delete()
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("KLYNT v${info.version}")
            .setDescription("Downloading update")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    /** 0..1 progress, or null when indeterminate. */
    fun queryProgress(context: Context, downloadId: Long): Float? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            if (total <= 0L) return null
            return (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
    }

    /** True when the download finished successfully. */
    fun isComplete(context: Context, downloadId: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    /** True when the download failed (then it is removed). */
    fun isFailed(context: Context, downloadId: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            return status == DownloadManager.STATUS_FAILED
        }
    }

    fun cancel(context: Context, downloadId: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(downloadId)
        } catch (_: Exception) {
        }
    }

    /** Launches the package installer for the downloaded file. */
    fun installIntent(context: Context): Intent? {
        val file = updateFile(context)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Location of the downloaded APK (shared with the installer intent). */
    fun updateFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return File(dir, APK_FILE)
    }
}
