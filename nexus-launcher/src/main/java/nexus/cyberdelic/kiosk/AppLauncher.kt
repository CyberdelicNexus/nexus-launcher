package nexus.cyberdelic.kiosk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class LaunchTarget(val packageName: String, val activityName: String, val category: String)

/** Finds an app's public front door, including VR-only ones, and opens it normally. */
class AppLauncher(context: Context) {
  private val appContext = context.applicationContext
  private val packageManager = appContext.packageManager

  fun resolve(packageName: String): LaunchTarget? {
    if (packageName.isEmpty()) return null
    return FRONT_DOORS.firstNotNullOfOrNull { category ->
      val intent = Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(packageName)
      packageManager
        .queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        .firstOrNull { it.activityInfo?.exported == true }
        ?.let { LaunchTarget(packageName, it.activityInfo.name, category) }
    }
  }

  fun icon(packageName: String): Drawable? =
    runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()

  fun launch(target: LaunchTarget): Boolean =
    runCatching {
        appContext.startActivity(
          Intent(Intent.ACTION_MAIN)
            .setClassName(target.packageName, target.activityName)
            .addCategory(target.category)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
      }
      .isSuccess

  private companion object {
    val FRONT_DOORS =
      listOf(
        Intent.CATEGORY_LAUNCHER,
        "com.oculus.intent.category.VR",
        "com.oculus.intent.category.2D",
        Intent.CATEGORY_LEANBACK_LAUNCHER,
      )
  }
}
