package takagi.ru.monica.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String?,
    val releaseUrl: String,
    val apkAssetName: String?,
    val apkDownloadUrl: String?,
    val releaseNotes: String?,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {
    private const val RELEASE_API_URL =
        "https://api.github.com/repos/Monica-Pass/Monica/releases/latest"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun checkLatestRelease(currentVersion: String): Result<UpdateCheckResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(RELEASE_API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Monica-Android")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("GitHub Releases request failed: HTTP ${response.code}")
                    }

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        throw IOException("GitHub Releases response is empty")
                    }

                    val release = json.decodeFromString(GitHubRelease.serializer(), body)
                    val latestVersion = release.tagName.trim()
                    val apkAsset = release.apkAsset()
                    UpdateCheckResult(
                        currentVersion = currentVersion,
                        latestVersion = latestVersion,
                        releaseName = release.name?.takeIf { it.isNotBlank() },
                        releaseUrl = release.htmlUrl,
                        apkAssetName = apkAsset?.name,
                        apkDownloadUrl = apkAsset?.downloadUrl,
                        releaseNotes = release.body?.takeIf { it.isNotBlank() },
                        isUpdateAvailable = compareVersionTags(latestVersion, currentVersion) > 0
                    )
                }
            }
        }

    suspend fun downloadApk(downloadUrl: String, outputDir: File, outputName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                outputDir.mkdirs()
                outputDir.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                    ?.forEach { it.delete() }

                val safeName = outputName
                    .ifBlank { "Monica-update.apk" }
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                    .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
                val outputFile = File(outputDir, safeName)

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "Monica-Android")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("APK download failed: HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IOException("APK download response is empty")
                    outputFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }

                if (outputFile.length() <= 0L) {
                    throw IOException("Downloaded APK is empty")
                }
                outputFile
            }
        }

    fun validateDownloadedApk(context: Context, apkFile: File): Result<Unit> =
        runCatching {
            val packageManager = context.packageManager
            val downloadedPackage = packageManager.getArchivePackageInfo(apkFile)
                ?: throw IOException("Downloaded APK package info is unreadable")
            val installedPackage = packageManager.getInstalledPackageInfo(context.packageName)

            if (downloadedPackage.packageName != context.packageName) {
                throw IOException("Downloaded APK package name does not match Monica")
            }

            val downloadedDigests = downloadedPackage.signingCertificateDigests()
            val installedDigests = installedPackage.signingCertificateDigests()
            if (downloadedDigests.isEmpty() || installedDigests.isEmpty()) {
                throw IOException("Downloaded APK signing certificate is unreadable")
            }

            val hasMatchingSigner = downloadedDigests.any { downloaded ->
                installedDigests.any { installed -> downloaded.contentEquals(installed) }
            }
            if (!hasMatchingSigner) {
                throw IOException("Downloaded APK signature does not match installed Monica")
            }
        }

    fun compareVersionTags(candidate: String, current: String): Int {
        val candidateParts = candidate.semanticVersionParts()
        val currentParts = current.semanticVersionParts()
        if (candidateParts.isEmpty() || currentParts.isEmpty()) {
            return candidate.trim().compareTo(current.trim(), ignoreCase = true)
        }

        val maxSize = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until maxSize) {
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (candidatePart != currentPart) {
                return candidatePart.compareTo(currentPart)
            }
        }
        return 0
    }

    private fun String.semanticVersionParts(): List<Int> {
        val semanticMatch = Regex("""(?i)v?(\d+)\.(\d+)\.(\d+)""").find(this)
        if (semanticMatch != null) {
            return semanticMatch.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
        }

        return Regex("\\d+")
            .findAll(this)
            .take(3)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.getArchivePackageInfo(apkFile: File): PackageInfo? =
        getPackageArchiveInfo(apkFile.absolutePath, signatureQueryFlags())

    @Suppress("DEPRECATION")
    private fun PackageManager.getInstalledPackageInfo(packageName: String): PackageInfo =
        getPackageInfo(packageName, signatureQueryFlags())

    private fun signatureQueryFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun PackageInfo.signingCertificateDigests(): List<ByteArray> =
        signaturesForVerification().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        }

    private fun PackageInfo.signaturesForVerification(): Array<Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptyArray()
            if (info.hasMultipleSigners()) {
                info.apkContentsSigners ?: emptyArray()
            } else {
                info.signingCertificateHistory ?: info.apkContentsSigners ?: emptyArray()
            }
        } else {
            @Suppress("DEPRECATION")
            signatures ?: emptyArray()
        }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList()
) {
    fun apkAsset(): GitHubReleaseAsset? =
        assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) ||
                asset.contentType?.equals("application/vnd.android.package-archive", ignoreCase = true) == true
        }
}

@Serializable
private data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("content_type") val contentType: String? = null
)
