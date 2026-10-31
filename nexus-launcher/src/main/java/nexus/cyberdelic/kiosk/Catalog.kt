package nexus.cyberdelic.kiosk

import org.json.JSONArray
import org.json.JSONObject

data class CatalogApp(
  val id: String,
  val title: String,
  val emoji: String = "",
  val packageName: String = "",
  val by: String = "",
  val medium: String = "",
  val duration: String = "",
  val intensity: String = "",
  val position: String = "",
  val capacity: String = "",
  val device: String = "",
  val input: String = "",
  val audio: String = "",
  val age: String = "",
  val consent: String = "",
  val summary: String = "",
  val frame: String = "",
  val closing: String = "",
  val setup: List<String> = emptyList(),
  val facilitate: List<String> = emptyList(),
  val reset: List<String> = emptyList(),
  val troubleshooting: List<Trouble> = emptyList(),
  val video: String = "",
  val videoUri: String = "",
  val videoMime: String = "",
) {
  /** A video entry opens a file in `packageName`, which is then a player app. */
  val isVideo: Boolean get() = video.isNotEmpty()
}

data class Trouble(val problem: String, val fix: String)

data class Catalog(val generated: String, val apps: List<CatalogApp>)

/** The menu Quest Fleet generates. Anything malformed is rejected whole, never partly shown. */
object CatalogCodec {
  const val SCHEMA = "nexus.catalog.v1"
  const val MAX_BYTES = 1024 * 1024
  private const val MAX_APPS = 200
  private val packagePattern = Regex("""^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$""")
  // Only the headset's own media library, never an arbitrary address.
  private val videoUriPattern = Regex("""^content://media/external/video/media/\d+$""")
  private val mimePattern = Regex("""^video/[A-Za-z0-9.+*-]+$""")

  fun parse(text: String): Catalog {
    val root = JSONObject(text)
    require(root.optString("schema") == SCHEMA) { "Unsupported menu schema." }
    val items = root.getJSONArray("apps")
    require(items.length() <= MAX_APPS) { "The menu has too many apps." }
    val apps = (0 until items.length()).map { parseApp(items.getJSONObject(it)) }
    require(apps.distinctBy { it.id }.size == apps.size) { "The menu repeats an app id." }
    return Catalog(root.optString("generated"), apps)
  }

  private fun parseApp(item: JSONObject): CatalogApp {
    val id = item.getString("id").trim()
    val title = item.getString("title").trim()
    require(id.isNotEmpty() && title.isNotEmpty()) { "Every menu app needs an id and a title." }
    val packageName = item.optString("package").trim()
    require(packageName.isEmpty() || packagePattern.matches(packageName)) {
      "Invalid package name for $title."
    }
    val videoUri = item.optString("videoUri").trim()
    require(videoUri.isEmpty() || videoUriPattern.matches(videoUri)) { "Invalid video address for $title." }
    val videoMime = item.optString("videoMime").trim()
    require(videoMime.isEmpty() || mimePattern.matches(videoMime)) { "Invalid video type for $title." }
    return CatalogApp(
      id = id,
      title = title,
      emoji = item.optString("emoji"),
      packageName = packageName,
      by = item.optString("by"),
      medium = item.optString("medium"),
      duration = item.optString("duration"),
      intensity = item.optString("intensity"),
      position = item.optString("position"),
      capacity = item.optString("capacity"),
      device = item.optString("device"),
      input = item.optString("input"),
      audio = item.optString("audio"),
      age = item.optString("age"),
      consent = item.optString("consent"),
      summary = item.optString("summary"),
      frame = item.optString("frame"),
      closing = item.optString("closing"),
      setup = strings(item.optJSONArray("setup")),
      facilitate = strings(item.optJSONArray("facilitate")),
      reset = strings(item.optJSONArray("reset")),
      troubleshooting = troubles(item.optJSONArray("troubleshooting")),
      video = item.optString("video"),
      videoUri = videoUri,
      videoMime = videoMime,
    )
  }

  private fun troubles(array: JSONArray?): List<Trouble> =
    if (array == null) emptyList()
    else (0 until array.length()).map { index ->
      array.getJSONObject(index).let { Trouble(it.optString("problem"), it.optString("fix")) }
    }

  private fun strings(array: JSONArray?): List<String> =
    if (array == null) emptyList() else (0 until array.length()).map { array.getString(it) }
}

object CatalogSearch {
  /** Every word must appear somewhere in the app's visible identity. */
  fun filter(apps: List<CatalogApp>, query: String): List<CatalogApp> {
    val words = query.lowercase().split(Regex("""\s+""")).filter(String::isNotEmpty)
    return apps.filter { app ->
      val haystack = listOf(app.title, app.by, app.medium, app.intensity).joinToString(" ").lowercase()
      words.all(haystack::contains)
    }
  }

  fun initials(title: String): String =
    title.split(Regex("""\s+""")).filter(String::isNotEmpty).take(2)
      .joinToString("") { it.take(1).uppercase() }
}
