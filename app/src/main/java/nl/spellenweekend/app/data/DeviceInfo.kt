package nl.spellenweekend.app.data

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat

/** Gegevens over de app op deze telefoon, voor het overzicht "Gebruikers" op de beheerwebsite. */
object DeviceInfo {
    fun fields(context: Context): Map<String, Any> {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
        return mapOf(
            "appVersion" to (info?.versionName ?: ""),
            "appVersionCode" to (info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L),
            "android" to Build.VERSION.RELEASE,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            "notifications" to NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
    }
}
