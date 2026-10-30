package nl.spellenweekend.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Een nieuwere versie op GitHub. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
)

/**
 * Controleert bij GitHub of er een nieuwere APK is, en kan die downloaden en installeren.
 * Releases heten "build-N"; N is gelijk aan de versionCode van de app.
 */
object Updater {
    private const val API = "https://api.github.com/repos/mheunen/Slimbo_weekend/releases/latest"
    const val DOWNLOAD_URL = "https://github.com/mheunen/Slimbo_weekend/releases/latest/download/Spellenweekend.apk"

    fun currentVersionCode(context: Context): Long = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }.getOrDefault(0L)

    fun currentVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** Geeft de nieuwere versie terug, of null als deze app de nieuwste is (of er geen verbinding is). */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val body = try {
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val json = JSONObject(body)
            val code = json.optString("tag_name").filter { it.isDigit() }.toLongOrNull() ?: return@runCatching null
            if (code <= currentVersionCode(context)) return@runCatching null
            val assets = json.optJSONArray("assets")
            var apk = DOWNLOAD_URL
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url", apk)
                }
            }
            UpdateInfo(
                versionCode = code,
                versionName = json.optString("name").substringAfterLast(' ').ifBlank { "1.0.$code" },
                notes = cleanNotes(json.optString("body")),
                apkUrl = apk,
            )
        }.getOrNull()
    }

    /** Markdown van de release-notes omzetten naar leesbare tekst, zonder de installatie-instructie. */
    private fun cleanNotes(md: String): String = md.lines()
        .map { it.replace("**", "").replace("`", "").trim() }
        .filter { it.isNotBlank() && !it.startsWith("Nieuw in deze versie") && !it.startsWith("Download ") }
        .joinToString("\n") { if (it.startsWith("- ")) "• " + it.drop(2) else it }
        .take(800)

    /** Mag deze app APK's installeren? (Android 8+: eenmalige toestemming per app) */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Download de APK naar de cache; [progress] krijgt 0..1 (of -1 als de grootte onbekend is). */
    suspend fun download(context: Context, url: String, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Spellenweekend.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode !in 200..299) error("Download mislukt (HTTP ${conn.responseCode})")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 256 * 1024) {
                            last = done
                            progress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        progress(1f)
        file
    }

    /** Start het Android-installatiescherm voor de gedownloade APK. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openInBrowser(context: Context, url: String = DOWNLOAD_URL) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
