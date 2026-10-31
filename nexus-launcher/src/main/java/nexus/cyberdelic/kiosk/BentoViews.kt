package nexus.cyberdelic.kiosk

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/** A dial like the Fleet duration tile, with the value in the middle. */
class RingView(context: Context, private val fraction: Float, private val centre: String, private val unit: String) : View(context) {
  private val density = resources.displayMetrics.density
  private val track = stroke(Color.parseColor("#33FFFFFF"))
  private val value = stroke(Color.parseColor("#C4B5FD"))
  private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
  }
  private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#D5D0E8")
    textAlign = Paint.Align.CENTER
    textSize = 15f * density
  }
  private val arc = RectF()

  private fun stroke(tint: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = tint
    style = Paint.Style.STROKE
    strokeWidth = 11f * density
    strokeCap = Paint.Cap.ROUND
  }

  override fun onDraw(canvas: Canvas) {
    val size = min(width, height).toFloat()
    val inset = track.strokeWidth
    val left = (width - size) / 2 + inset
    val top = (height - size) / 2 + inset
    arc.set(left, top, left + size - 2 * inset, top + size - 2 * inset)
    canvas.drawArc(arc, 0f, 360f, false, track)
    canvas.drawArc(arc, -90f, 360f * fraction.coerceIn(0f, 1f), false, value)
    big.textSize = (if (centre.length > 3) 26f else 36f) * density
    val middle = height / 2f + big.textSize * 0.34f - (if (unit.isEmpty()) 0f else 8f * density)
    canvas.drawText(centre, width / 2f, middle, big)
    if (unit.isNotEmpty()) canvas.drawText(unit, width / 2f, middle + 20f * density, small)
  }
}

/** Five rising bars, green to red. With no known level, every bar lights as a variable range. */
class MeterView(context: Context, private val range: BentoFacts.Intensity?) : View(context) {
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val colors = listOf("#4ADE80", "#A3E635", "#FACC15", "#FB923C", "#F87171").map(Color::parseColor)
  private val bar = RectF()

  override fun onDraw(canvas: Canvas) {
    val gap = 8f * density
    val barWidth = (width - 4 * gap) / 5
    for (level in 1..5) {
      val high = height * (0.3f + level * 0.14f)
      val x = (level - 1) * (barWidth + gap)
      bar.set(x, height - high, x + barWidth, height.toFloat())
      val lit = range == null || level <= range.max
      paint.color = if (lit) colors[level - 1] else Color.parseColor("#26FFFFFF")
      paint.alpha = if (range != null && lit && level < range.min) 110 else if (lit) 255 else 38
      canvas.drawRoundRect(bar, 8f * density, 8f * density, paint)
    }
  }
}
