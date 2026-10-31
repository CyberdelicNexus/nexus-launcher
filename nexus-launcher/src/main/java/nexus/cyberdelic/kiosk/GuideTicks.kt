package nexus.cyberdelic.kiosk

import android.content.Context

/** Which guide steps a facilitator has ticked. Survives the launched app replacing this one. */
class GuideTicks(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences("nexus_ticks", Context.MODE_PRIVATE)

  private fun key(part: String, index: Int) = "$part:$index"
  private fun read(id: String): Set<String> = preferences.getStringSet(id, emptySet()).orEmpty()

  fun isDone(id: String, part: String, index: Int): Boolean = key(part, index) in read(id)

  fun any(id: String): Boolean = read(id).isNotEmpty()

  fun toggle(id: String, part: String, index: Int) {
    val entry = key(part, index)
    val current = read(id)
    preferences.edit().putStringSet(id, if (entry in current) current - entry else current + entry).apply()
  }

  fun clear(id: String) = preferences.edit().remove(id).apply()
}
