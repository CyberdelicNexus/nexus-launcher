package nexus.cyberdelic.launcher

import android.content.Context
import java.io.File

/** Keeps the last good menu in app-private storage; a bad delivery never replaces it. */
class CatalogStore(context: Context) {
  private val appContext = context.applicationContext
  private val active = File(appContext.filesDir, "nexus-catalog.json")
  private val preferences = appContext.getSharedPreferences("nexus_catalog", Context.MODE_PRIVATE)

  val incoming: File = File(appContext.filesDir, "nexus-catalog.incoming")

  fun load(): Catalog? =
    runCatching { CatalogCodec.parse(active.readText()) }.getOrNull()

  val lastError: String
    get() = preferences.getString(KEY_ERROR, "").orEmpty()

  fun activateIncoming(): Boolean {
    val error =
      runCatching {
        require(incoming.length() in 1..CatalogCodec.MAX_BYTES.toLong()) { "Menu file size is invalid." }
        CatalogCodec.parse(incoming.readText())
        check(incoming.renameTo(active)) { "Could not activate the menu." }
      }.exceptionOrNull()
    incoming.delete()
    preferences.edit().putString(KEY_ERROR, error?.message ?: "").apply()
    return error == null
  }

  private companion object {
    const val KEY_ERROR = "last_error"
  }
}

/** The running panel subscribes here so a Fleet push appears without reopening the app. */
object CatalogEvents {
  @Volatile var onChanged: (() -> Unit)? = null
}
