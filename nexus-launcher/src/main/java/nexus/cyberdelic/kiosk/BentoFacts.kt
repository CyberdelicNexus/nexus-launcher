package nexus.cyberdelic.kiosk

/** Turns a guide's free text into glanceable values. Mirrors quest_fleet/public/bento.js. */
object BentoFacts {
  data class Duration(val minutes: Double, val label: String)
  data class Guests(val count: Int, val label: String)
  data class Intensity(val min: Int, val max: Int)

  val LEVELS = listOf("", "Very gentle", "Gentle", "Moderate", "Strong", "Very strong")

  /** A short headline: the first clause, at most about 30 characters. */
  fun short(text: String): String {
    val first = text.split(Regex(""";|\(|\.\s|,\s| — | – | - """)).first().trim()
    if (first.length <= 30) return first
    var out = ""
    for (word in first.split(Regex("""\s+"""))) {
      if ("$out $word".trim().length > 28) break
      out = "$out $word".trim()
    }
    return "${out.ifEmpty { first.take(24) }}…"
  }

  fun intensity(text: String): Intensity? {
    val t = text.lowercase()
    val found = listOf(
      Regex("""very (mild|gentle|low)""") to 1,
      Regex("""\b(mild|gentle|low|calm|soft)""") to 2,
      Regex("""moderate|medium""") to 3,
      Regex("""(?<!very )strong|intense|high""") to 4,
      Regex("""very strong|extreme|very intense""") to 5,
    ).filter { it.first.containsMatchIn(t) }.map { it.second }
    return if (found.isEmpty()) null else Intensity(found.min(), found.max())
  }

  fun intensityHeadline(text: String): String {
    val range = intensity(text)
    return when {
      range != null && range.min == range.max -> LEVELS[range.max]
      range != null -> "${LEVELS[range.min]} to ${LEVELS[range.max]}"
      Regex("(?i)progressive").containsMatchIn(text) -> "Builds gradually"
      Regex("(?i)controlled").containsMatchIn(text) -> "Guest-controlled"
      Regex("(?i)variable").containsMatchIn(text) -> "Variable"
      else -> short(text)
    }
  }

  fun duration(text: String): Duration? {
    val t = text.lowercase()
    Regex("""(\d+):(\d{2})""").find(t)?.let {
      val (m, s) = it.destructured
      return Duration(m.toDouble() + s.toDouble() / 60, "$m:$s")
    }
    Regex("""(\d+)\s*(?:–|-|to)\s*(\d+)\s*min""").find(t)?.let {
      val (a, b) = it.destructured
      return Duration(b.toDouble(), "$a–$b")
    }
    Regex("""(\d+)\s*(?:min|minute)""").find(t)?.let {
      return Duration(it.groupValues[1].toDouble(), it.groupValues[1])
    }
    Regex("""(\d+(?:\.\d+)?)\s*(?:h\b|hour)""").find(t)?.let {
      return Duration(it.groupValues[1].toDouble() * 60, "${it.groupValues[1]} h")
    }
    return null
  }

  fun isOpenEnded(text: String) = Regex("(?i)user|open|flex|self|varies|free").containsMatchIn(text)

  fun guests(text: String): Guests? {
    if (Regex("""^\s*\[""").containsMatchIn(text)) return null
    Regex("""(\d+)(?:\s*(?:–|-|to)\s*(\d+))?""").find(text)?.let {
      val (a, b) = it.destructured
      return Guests((b.ifEmpty { a }).toInt(), if (b.isEmpty()) a else "$a–$b")
    }
    return if (Regex("""(?i)\b(solo|one|single)\b""").containsMatchIn(text)) Guests(1, "1") else null
  }

  fun needsExtraConsent(text: String) =
    !Regex("(?i)^(none|no extra|standard|not needed|no separate)").containsMatchIn(text.trim())

  fun ageNumber(text: String): String? = Regex("""\d+""").find(text)?.value

  /** Icons a text mentions, in rule order. */
  private fun icons(text: String, rules: List<Pair<String, String>>): List<String> {
    val t = text.lowercase()
    return rules.filter { Regex(it.first).containsMatchIn(t) }.map { it.second }
  }

  fun deviceIcons(text: String) = icons(text, listOf(
    "quest|headset|hmd|vr\\b|pico|vive|index" to "🥽",
    "\\bpc\\b|desktop|steam|tower|rig" to "🖥️",
    "laptop|macbook" to "💻",
    "dome|projector|projection|screen" to "📽️",
    "bed|mat" to "🛏️",
    "visor|glasses|specs" to "👓",
    "phone|tablet" to "📱",
  )).take(3)

  fun inputIcons(text: String) = icons(text, listOf(
    "hand|touch|haptic|finger" to "🖐️",
    "controller|gamepad|joystick|trigger" to "🎮",
    "passive|watch|observ|none|no input|look|gaze|eye" to "👁️",
    "body|movement|move|dance|walk|gestu" to "🕺",
    "breath" to "🌬️",
  )).take(3)

  fun audioIcon(text: String): Pair<String, String> {
    val t = text.lowercase()
    return when {
      Regex("no audio|silent|no sound|none").containsMatchIn(t) -> "🔇" to "No audio"
      Regex("headphone|earphone|headset audio").containsMatchIn(t) -> "🎧" to "Headphones"
      Regex("speaker|room audio|ambient").containsMatchIn(t) -> "🔊" to "Speakers"
      else -> "🎧" to short(text)
    }
  }

  val POSITIONS = listOf("🪑" to "Seated", "🧍" to "Standing", "🛌" to "Lying")

  fun positions(text: String): List<String> {
    val t = text.lowercase()
    return listOfNotNull(
      "Seated".takeIf { Regex("seat|sit|chair").containsMatchIn(t) },
      "Standing".takeIf { Regex("stand|walk|room").containsMatchIn(t) },
      "Lying".takeIf { Regex("lying|lie|lay|reclin|bed|floor|mat").containsMatchIn(t) },
    )
  }
}
