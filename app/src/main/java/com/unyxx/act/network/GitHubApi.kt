package com.unyxx.act.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Raw GitHub release payload (subset we care about). */
data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList()
)

/** Single release asset. */
data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long = 0L
)

/**
 * Minimal GitHub Releases client (OkHttp directly — no Retrofit needed
 * for a single endpoint).
 */
object GitHubApi {
    private const val LATEST_URL = "https://api.github.com/repos/U-Nyxx/Klynt/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** @throws Exception on network/parse failure (caller maps to UI state). */
    suspend fun fetchLatest(): GitHubRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "KLYNT-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IllegalStateException("Empty release payload")
            gson.fromJson(body, GitHubRelease::class.java)
                ?: throw IllegalStateException("Unparseable release payload")
        }
    }
}
