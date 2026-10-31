package nexus.cyberdelic.launcher

import android.view.KeyEvent

/**
 * Notices when an app the launcher opened has really ended. Opening the Meta menu raises
 * FocusPlaceholderActivity and leaves the app alone; only the shell's Home activity counts.
 */
class ReturnPolicy(private val launcherPackage: String) {
  enum class Decision { IGNORE, RETURN, FINISHED }

  private var target: String? = null
  private var seenAtMs = NOT_SEEN

  fun arm(packageName: String) {
    target = packageName
    seenAtMs = NOT_SEEN
  }

  fun clear() {
    target = null
    seenAtMs = NOT_SEEN
  }

  val armedPackage: String?
    get() = target

  fun observe(packageName: String, className: String, nowMs: Long): Decision {
    val armed = target ?: return Decision.IGNORE
    if (packageName == armed) {
      if (seenAtMs == NOT_SEEN) seenAtMs = nowMs
      return Decision.IGNORE
    }
    if (seenAtMs == NOT_SEEN) return Decision.IGNORE
    // The launcher's own window also closes out a session, however the wearer got back to it.
    if (packageName == launcherPackage) return Decision.FINISHED
    if (packageName != HOME_PACKAGE || className != HOME_ACTIVITY) return Decision.IGNORE
    // Shell windows can trail the app's own start; they are not the wearer leaving it.
    return if (nowMs - seenAtMs < LAUNCH_TAIL_MS) Decision.IGNORE else Decision.RETURN
  }

  companion object {
    const val LAUNCH_TAIL_MS = 1_500L
    const val HOME_PACKAGE = "com.oculus.vrshell"
    const val HOME_ACTIVITY = "com.oculus.vrshell.HomeActivity"
    private const val NOT_SEEN = Long.MIN_VALUE
  }
}

/** Tracks the facilitator's exit gesture: both headset volume buttons held down together. */
class VolumeChord {
  enum class Change { NONE, BOTH_DOWN, RELEASED }

  private var upHeld = false
  private var downHeld = false

  fun update(keyCode: Int, pressed: Boolean): Change {
    val before = upHeld && downHeld
    when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP -> upHeld = pressed
      KeyEvent.KEYCODE_VOLUME_DOWN -> downHeld = pressed
      else -> return Change.NONE
    }
    val now = upHeld && downHeld
    return when {
      now && !before -> Change.BOTH_DOWN
      before && !now -> Change.RELEASED
      else -> Change.NONE
    }
  }

  companion object {
    const val HOLD_MS = 1_000L
  }
}
