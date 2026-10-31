package nexus.cyberdelic.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Remembers which app the launcher opened, across this process being stopped. */
object ReturnSession {
  private const val PREFERENCES = "nexus_return"
  private const val KEY_PACKAGE = "target"

  fun arm(context: Context, target: LaunchTarget) {
    prefs(context).edit().putString(KEY_PACKAGE, target.packageName).commit()
  }

  fun clear(context: Context) {
    prefs(context).edit().clear().commit()
  }

  fun target(context: Context): String? = prefs(context).getString(KEY_PACKAGE, null)

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

/**
 * Brings the launcher back when a facilitator holds both volume buttons, or when an app the
 * launcher opened has ended. Volume presses are passed on untouched, and it reads only which
 * top-level window is in front, never what is on screen. Fleet switches it on for each headset.
 */
class BackToLauncherService : AccessibilityService() {
  private val handler = Handler(Looper.getMainLooper())
  private val policy by lazy(LazyThreadSafetyMode.NONE) { ReturnPolicy(packageName) }
  private val chord = VolumeChord()
  private val chordHeld = Runnable {
    Log.i(TAG, "event=volume-chord-held")
    returnToLauncher()
  }

  override fun onKeyEvent(event: KeyEvent?): Boolean {
    if (event == null) return false
    if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      Log.i(TAG, "event=volume-key code=${event.keyCode} action=${event.action}")
    }
    when (chord.update(event.keyCode, event.action == KeyEvent.ACTION_DOWN)) {
      VolumeChord.Change.BOTH_DOWN -> handler.postDelayed(chordHeld, VolumeChord.HOLD_MS)
      VolumeChord.Change.RELEASED -> handler.removeCallbacks(chordHeld)
      VolumeChord.Change.NONE -> Unit
    }
    return false
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    val eventPackage = event.packageName?.toString()?.trim().orEmpty()
    if (eventPackage.isEmpty()) return
    val target = ReturnSession.target(this)
    if (target == null) {
      policy.clear()
      return
    }
    if (policy.armedPackage != target) policy.arm(target)
    val className = event.className?.toString()?.trim().orEmpty()
    val stateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    if (stateChange) Log.i(TAG, "event=window package=$eventPackage class=$className")
    when (policy.observe(eventPackage, if (stateChange) className else "", SystemClock.elapsedRealtime())) {
      ReturnPolicy.Decision.IGNORE -> Unit
      ReturnPolicy.Decision.FINISHED -> finish()
      ReturnPolicy.Decision.RETURN -> {
        Log.i(TAG, "event=home-after-app")
        handler.postDelayed(::returnToLauncher, RETURN_DELAY_MS)
      }
    }
  }

  private fun returnToLauncher() {
    finish()
    runCatching {
      startActivity(
        Intent(Intent.ACTION_MAIN, null, this, NexusActivity::class.java)
          .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TOP or
              Intent.FLAG_ACTIVITY_SINGLE_TOP,
          ),
      )
    }
  }

  private fun finish() {
    policy.clear()
    ReturnSession.clear(this)
  }

  override fun onInterrupt() = Unit

  private companion object {
    const val TAG = "NexusReturn"
    const val RETURN_DELAY_MS = 250L
  }
}
