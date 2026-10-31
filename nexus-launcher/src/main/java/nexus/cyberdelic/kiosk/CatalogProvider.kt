package nexus.cyberdelic.kiosk

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Fleet delivery route: `content write --user N --uri .../catalog`, then `content call` readback.
 * Android's `android:permission` admits only DUMP holders, which in practice means the ADB shell.
 * Using a provider rather than shared storage reaches the right file on secondary Quest accounts.
 */
class CatalogProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    if (uri.lastPathSegment != "catalog" || !mode.startsWith("w")) {
      throw FileNotFoundException("Only the menu can be written.")
    }
    val store = CatalogStore(requireNotNull(context))
    return ParcelFileDescriptor.open(
      store.incoming,
      ParcelFileDescriptor.MODE_WRITE_ONLY or
        ParcelFileDescriptor.MODE_CREATE or
        ParcelFileDescriptor.MODE_TRUNCATE,
      Handler(Looper.getMainLooper()),
    ) { _ ->
      if (store.activateIncoming()) CatalogEvents.onChanged?.invoke()
    }
  }

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
    if (method != "status") return null
    val store = CatalogStore(requireNotNull(context))
    val catalog = store.load()
    return Bundle().apply {
      putString("generated", catalog?.generated.orEmpty())
      putInt("apps", catalog?.apps?.size ?: 0)
      putString("error", store.lastError)
    }
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
  ): Cursor? = null
  override fun getType(uri: Uri): String = "application/json"
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<out String>?,
  ): Int = 0
}
