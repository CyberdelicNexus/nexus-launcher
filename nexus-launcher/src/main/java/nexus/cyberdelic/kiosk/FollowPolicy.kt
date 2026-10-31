package nexus.cyberdelic.kiosk

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Keeps a wide panel in front of the wearer without chasing every glance. It holds still while
 * they read across it, starts once they have clearly turned away, and settles straight ahead.
 */
class FollowPolicy(
  private val startDegrees: Float = 42f,
  private val settleDegrees: Float = 4f,
  private val ratePerSecond: Float = 3.5f,
) {
  var yaw: Float = 0f
    private set
  var following = false
    private set

  fun reset(viewerYaw: Float) {
    yaw = viewerYaw
    following = false
  }

  /** Advances one frame. Returns true when the panel moved. */
  fun update(viewerYaw: Float, seconds: Float): Boolean {
    val error = shortestAngle(viewerYaw - yaw)
    val degrees = abs(error) * 180f / PI.toFloat()
    if (!following && degrees >= startDegrees) following = true
    if (!following) return false
    yaw = wrap(yaw + error * (1f - exp(-ratePerSecond * seconds)))
    if (degrees <= settleDegrees) following = false
    return true
  }

  companion object {
    fun yawOf(forwardX: Float, forwardZ: Float): Float = atan2(forwardX, forwardZ)
    fun forwardX(yaw: Float): Float = sin(yaw)
    fun forwardZ(yaw: Float): Float = cos(yaw)

    fun shortestAngle(radians: Float): Float = wrap(radians)

    private fun wrap(radians: Float): Float {
      var value = radians
      val turn = (2 * PI).toFloat()
      while (value > PI) value -= turn
      while (value < -PI) value += turn
      return value
    }
  }
}
