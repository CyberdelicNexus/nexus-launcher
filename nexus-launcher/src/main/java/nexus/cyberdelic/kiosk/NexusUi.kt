package nexus.cyberdelic.kiosk

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * One curved surface: floating app logos, and beside them the selected experience's guide, which
 * pushes the grid to the left when it opens. Native Views, because Horizon can refuse the keyboard
 * inside a Compose panel.
 */
class NexusUi(
  private val context: Context,
  private val store: CatalogStore,
  private val launcher: AppLauncher,
  private val ticks: GuideTicks,
  private val onToggleView: () -> Unit,
  private val onDistanceChange: (Int) -> Unit,
) {
  private enum class Tab(val title: String) {
    OVERVIEW("Overview"), SETUP("Setup"), FACILITATE("Facilitate"), RESET("Reset"), HELP("Help")
  }

  private var apps: List<CatalogApp> = emptyList()
  private var selectedId: String? = null
  private var tab = Tab.OVERVIEW
  private var query = ""
  private var received = false
  private val icons = HashMap<String, Drawable?>()

  private val page = vertical()
  private val grid = vertical()
  private val gridScroll = ScrollView(context)
  private val viewButton = pillButton("")
  private val count = label("", 16f, MUTED)
  private val preferences = context.getSharedPreferences("nexus_view", Context.MODE_PRIVATE)
  private var iconSize = preferences.getInt(KEY_ICON_SIZE, ICON_DEFAULT).coerceIn(ICON_MIN, ICON_MAX)
  private val distance = label("", 17f, TEXT, bold = true).apply { gravity = Gravity.CENTER }

  private val guide = vertical()
  private val guideTitle = label("", 34f, TEXT, bold = true)
  private val guideBy = label("", 18f, MUTED)
  private val tabs = LinearLayout(context)
  private val tabBody = vertical()
  private val tabScroll = ScrollView(context).apply { addView(tabBody) }
  private val readiness = label("", 20f, MUTED, bold = true)
  private val launchButton = Button(context)

  init {
    page.setPadding(dp(24), dp(10), dp(24), dp(12))
    page.addView(logo(), matchWrap())
    val body = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_HORIZONTAL
    }
    gridScroll.addView(grid)
    gridScroll.isVerticalFadingEdgeEnabled = true
    gridScroll.setFadingEdgeLength(dp(30))
    gridScroll.isVerticalScrollBarEnabled = false
    body.addView(rail(), LinearLayout.LayoutParams(dp(RAIL_WIDTH), -1).apply { marginEnd = dp(PANEL_GAP) })
    body.addView(gridScroll, LinearLayout.LayoutParams(dp(BODY_WIDTH), -1))
    buildGuide()
    body.addView(guide, LinearLayout.LayoutParams(dp(GUIDE_WIDTH), -1).apply { marginStart = dp(PANEL_GAP) })
    page.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
    guide.visibility = View.GONE
  }

  fun bind(root: View) {
    (page.parent as? ViewGroup)?.removeView(page)
    (root as ViewGroup).addView(page, FrameLayout.LayoutParams(-1, -1))
  }

  private fun logo(): View =
    LinearLayout(context).apply {
      gravity = Gravity.CENTER
      addView(
        ImageView(context).apply {
          setImageResource(R.drawable.nexus_logo)
          adjustViewBounds = true
        },
        LinearLayout.LayoutParams(-2, dp(64)),
      )
    }

  /** Every control in one column beside the grid, within easy reach of a ray or a hand. */
  private fun rail(): View =
    vertical().apply {
      background = rounded(GLASS, dp(28).toFloat())
      setPadding(dp(14), dp(16), dp(14), dp(16))
      addView(label("Cyberdelic Kiosk", 19f, TEXT, bold = true))
      addView(count, matchWrap().apply { bottomMargin = dp(14) })
      addView(searchField(), LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(16) })
      addView(label("Thumbnail size", 16f, MUTED_LIGHT))
      addView(sizeSlider(), LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(12) })
      addView(
        LinearLayout(context).apply {
          addView(label("Distance", 16f, MUTED_LIGHT), LinearLayout.LayoutParams(0, -2, 1f))
          addView(distance, LinearLayout.LayoutParams(-2, -2))
        },
        matchWrap().apply { bottomMargin = dp(6) },
      )
      val closer = pillButton("Closer").apply { setOnClickListener { onDistanceChange(-1) } }
      addView(closer, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(8) })
      val farther = pillButton("Farther").apply { setOnClickListener { onDistanceChange(1) } }
      addView(farther, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(16) })
      viewButton.setOnClickListener { onToggleView() }
      addView(viewButton, LinearLayout.LayoutParams(-1, dp(54)))
    }

  private fun searchField(): EditText =
    EditText(context).apply {
      hint = "Search"
      setHintTextColor(MUTED)
      setTextColor(TEXT)
      textSize = 19f
      isSingleLine = true
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
      background = rounded(FIELD, dp(26).toFloat())
      setPadding(dp(20), 0, dp(20), 0)
      addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
          query = s?.toString().orEmpty()
          renderGrid()
        }
      })
    }

  private fun sizeSlider(): SeekBar =
    SeekBar(context).apply {
      max = (ICON_MAX - ICON_MIN) / ICON_STEP
      progress = (iconSize - ICON_MIN) / ICON_STEP
      progressTintList = ColorStateList.valueOf(ACCENT_LIGHT)
      thumbTintList = ColorStateList.valueOf(TEXT)
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
          if (!fromUser) return
          iconSize = ICON_MIN + value * ICON_STEP
          preferences.edit().putInt(KEY_ICON_SIZE, iconSize).apply()
          renderGrid()
        }
        override fun onStartTrackingTouch(bar: SeekBar?) = Unit
        override fun onStopTrackingTouch(bar: SeekBar?) = Unit
      })
    }

  private fun buildGuide() {
    guide.background = rounded(CARD, dp(32).toFloat())
    guide.setPadding(dp(26), dp(20), dp(26), dp(20))
    val top = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      val heading = vertical().apply {
        addView(guideTitle)
        addView(guideBy)
      }
      addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
      addView(pillButton("Close").apply { setOnClickListener { select(null) } }, LinearLayout.LayoutParams(dp(110), dp(52)))
    }
    guide.addView(top, matchWrap())
    tabs.orientation = LinearLayout.HORIZONTAL
    guide.addView(tabs, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
    guide.addView(tabScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
    val footer = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      addView(readiness, LinearLayout.LayoutParams(0, -2, 1f))
      style(launchButton, ACCENT, Color.WHITE, 22)
      launchButton.textSize = 24f
      launchButton.setOnClickListener { launchSelected() }
      addView(launchButton, LinearLayout.LayoutParams(dp(260), dp(72)).apply { marginStart = dp(14) })
    }
    guide.addView(footer, matchWrap().apply { topMargin = dp(12) })
  }

  /** Reads the saved menu and rechecks what is installed. */
  fun reload() {
    val catalog = store.load()
    received = catalog != null
    apps = catalog?.apps.orEmpty()
    icons.clear()
    if (apps.none { it.id == selectedId }) selectedId = null
    count.text = if (received) "${apps.size} experience${if (apps.size == 1) "" else "s"}" else "Waiting for the menu"
    renderGrid()
    renderGuide(keepScroll = true)
  }

  fun showDistance(step: Int) {
    distance.text = step.toString()
  }

  fun showViewMode(passthrough: Boolean) {
    viewButton.text = if (passthrough) "Switch to VR" else "Passthrough"
  }

  private fun select(id: String?) {
    if (id != selectedId) tab = Tab.OVERVIEW
    selectedId = id
    renderGrid()
    renderGuide(keepScroll = false)
  }

  private fun renderGrid() {
    grid.removeAllViews()
    val shown = CatalogSearch.filter(apps, query)
    if (shown.isEmpty()) {
      val message =
        if (apps.isEmpty()) "No menu on this headset yet.\nSend it from Quest Fleet with Push menu to headsets."
        else "Nothing matches your search."
      grid.addView(
        label(message, 26f, TEXT, shadow = true).apply {
          gravity = Gravity.CENTER
          setPadding(dp(8), dp(90), dp(8), 0)
        },
        matchWrap(),
      )
      return
    }
    // Fixed cells keep the logos together; the block is sized to them and centred on the panel.
    val room = if (selectedId == null) BODY_WIDTH else BODY_WIDTH - GUIDE_WIDTH - PANEL_GAP
    val cell = iconSize + TILE_GAP
    val columns = (room / cell).coerceAtLeast(2)
    (gridScroll.layoutParams as? LinearLayout.LayoutParams)?.let {
      it.width = dp(minOf(room, columns * cell))
      gridScroll.layoutParams = it
    }
    shown.chunked(columns).forEach { row ->
      val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
      row.forEach { line.addView(tile(it), LinearLayout.LayoutParams(dp(cell), dp(iconSize + TILE_TEXT_HEIGHT))) }
      grid.addView(line, matchWrap())
    }
  }

  private fun tile(app: CatalogApp): View {
    val available = launcher.resolve(app.packageName) != null
    val selected = app.id == selectedId
    val holder = FrameLayout(context).apply {
      addView(artwork(app), FrameLayout.LayoutParams(-1, -1))
      if (selected) foreground = rounded(Color.TRANSPARENT, iconRadius(), ACCENT_LIGHT)
    }
    val base = if (selected) 1.06f else 1f
    return LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(dp(4), dp(14), dp(4), dp(4))
      isClickable = true
      isFocusable = true
      contentDescription = app.title
      addView(holder, LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
      addView(
        label(app.title, 20f, TEXT, bold = true, shadow = true).apply {
          gravity = Gravity.CENTER
          maxLines = 2
          ellipsize = TextUtils.TruncateAt.END
          setPadding(0, dp(10), 0, 0)
        },
        matchWrap(),
      )
      if (!available) {
        addView(
          label(if (app.packageName.isEmpty()) "Guide only" else "Not on this headset", 15f, MUTED_LIGHT, shadow = true)
            .apply { gravity = Gravity.CENTER },
          matchWrap(),
        )
        holder.alpha = if (app.packageName.isEmpty()) 0.9f else 0.45f
      }
      holder.scaleX = base
      holder.scaleY = base
      setOnHoverListener { _, event ->
        when (event.actionMasked) {
          MotionEvent.ACTION_HOVER_ENTER -> holder.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
          MotionEvent.ACTION_HOVER_EXIT -> holder.animate().scaleX(base).scaleY(base).setDuration(160).start()
        }
        false
      }
      setOnClickListener { select(if (selected) null else app.id) }
    }
  }

  /** The app's own logo, or the guide's emoji on a colour plate when there is no installed app. */
  private fun artwork(app: CatalogApp): View {
    val drawable =
      if (app.packageName.isEmpty()) null
      else icons.getOrPut(app.packageName) { launcher.icon(app.packageName) }
    val view: View =
      if (drawable != null) {
        ImageView(context).apply {
          setImageDrawable(drawable)
          scaleType = ImageView.ScaleType.CENTER_CROP
          setBackgroundColor(PLATE)
        }
      } else {
        label(app.emoji.ifEmpty { CatalogSearch.initials(app.title) }, iconSize * 0.4f, TEXT).apply {
          gravity = Gravity.CENTER
          background = plate(app.id)
        }
      }
    view.outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(target: View, outline: Outline) =
        outline.setRoundRect(0, 0, target.width, target.height, iconRadius())
    }
    view.clipToOutline = true
    return view
  }

  private fun iconRadius() = dp(iconSize) * 0.23f

  private fun plate(id: String): GradientDrawable {
    val hue = Math.floorMod(id.hashCode(), 360).toFloat()
    return GradientDrawable(
      GradientDrawable.Orientation.TL_BR,
      intArrayOf(
        Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.62f)),
        Color.HSVToColor(floatArrayOf((hue + 50f) % 360f, 0.72f, 0.32f)),
      ),
    )
  }

  private fun renderGuide(keepScroll: Boolean) {
    val app = apps.firstOrNull { it.id == selectedId }
    guide.visibility = if (app == null) View.GONE else View.VISIBLE
    if (app == null) return
    val scroll = tabScroll.scrollY
    guideTitle.text = app.title
    guideBy.text = listOf(app.by, app.medium).filter(String::isNotEmpty).joinToString("  ·  ")
    renderTabs(app)
    tabBody.removeAllViews()
    when (tab) {
      Tab.OVERVIEW -> overview(app)
      Tab.SETUP -> checklist(app, "setup", app.setup)
      Tab.FACILITATE -> {
        checklist(app, "facilitate", app.facilitate)
        if (app.closing.isNotEmpty()) tabBody.addView(quoteCard("Closing question", app.closing), spaced())
      }
      Tab.RESET -> checklist(app, "reset", app.reset)
      Tab.HELP -> help(app)
    }
    tabScroll.post { tabScroll.scrollTo(0, if (keepScroll) scroll else 0) }
    renderReadiness(app)
  }

  private fun renderTabs(app: CatalogApp) {
    tabs.removeAllViews()
    Tab.values().forEach { option ->
      val steps = stepsOf(app, option)
      val done = steps.indices.count { ticks.isDone(app.id, partOf(option), it) }
      val title = if (steps.isEmpty()) option.title else "${option.title} $done/${steps.size}"
      val button = pillButton(title).apply {
        textSize = 17f
        style(this, if (option == tab) ACCENT else FIELD, TEXT, 22)
        setOnClickListener {
          tab = option
          renderGuide(keepScroll = false)
        }
      }
      tabs.addView(button, LinearLayout.LayoutParams(0, -1, if (steps.isEmpty()) 1f else 1.35f).apply { marginEnd = dp(6) })
    }
  }

  private fun partOf(option: Tab) = option.name.lowercase()

  private fun stepsOf(app: CatalogApp, option: Tab) =
    when (option) {
      Tab.SETUP -> app.setup
      Tab.FACILITATE -> app.facilitate
      Tab.RESET -> app.reset
      else -> emptyList()
    }

  private fun overview(app: CatalogApp) {
    val say = app.frame.ifEmpty { app.summary }
    if (say.isNotEmpty()) tabBody.addView(quoteCard(if (app.frame.isEmpty()) "About" else "Say to the guest", say), matchWrap())
    if (app.duration.isNotEmpty() || app.intensity.isNotEmpty()) {
      row(durationTile(app), 1f, intensityTile(app), 1.6f)
    }
    row(capacityTile(app), 1f, positionTile(app), 1f, audioTile(app), 1f)
    row(consentTile(app), 1.8f, ageTile(app), 1f)
    row(iconTile("Device", app.device, BentoFacts.deviceIcons(app.device)), 1f, iconTile("Input", app.input, BentoFacts.inputIcons(app.input)), 1f)
  }

  private fun row(vararg cells: Any?) {
    val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    for (index in cells.indices step 2) {
      val view = cells[index] as? View ?: continue
      line.addView(view, LinearLayout.LayoutParams(0, -1, cells[index + 1] as Float).apply { if (line.childCount > 0) marginStart = dp(10) })
    }
    if (line.childCount > 0) tabBody.addView(line, spaced())
  }

  private fun spaced() = matchWrap().apply { topMargin = dp(10) }

  private fun card(title: String, tint: Int = WELL) = vertical().apply {
    background = rounded(tint, dp(20).toFloat())
    setPadding(dp(16), dp(12), dp(16), dp(14))
    addView(label(title, 16f, MUTED_LIGHT, bold = true))
  }

  private fun headline(value: String, size: Float = 26f, color: Int = TEXT) =
    label(value, size, color, bold = true).apply { setPadding(0, dp(4), 0, 0) }

  private fun quoteCard(title: String, value: String) = card(title, QUOTE).apply {
    addView(label(value, 24f, TEXT).apply { setPadding(0, dp(6), 0, 0); setLineSpacing(dp(3).toFloat(), 1f) })
  }

  private fun durationTile(app: CatalogApp): View? {
    if (app.duration.isEmpty()) return null
    val parsed = BentoFacts.duration(app.duration)
    val open = parsed == null && BentoFacts.isOpenEnded(app.duration)
    return card("Duration").apply {
      val ring = RingView(
        context,
        if (parsed != null) (parsed.minutes / 60).toFloat() else if (open) 0.7f else 0f,
        parsed?.label ?: if (open) "∞" else "?",
        if (parsed != null && !parsed.label.contains("h")) "min" else "",
      )
      addView(ring, LinearLayout.LayoutParams(-1, dp(136)).apply { topMargin = dp(6) })
      if (parsed == null) addView(headline(if (open) "Flexible" else BentoFacts.short(app.duration), 20f).apply { gravity = Gravity.CENTER }, matchWrap())
    }
  }

  private fun intensityTile(app: CatalogApp): View? {
    if (app.intensity.isEmpty()) return null
    return card("Intensity").apply {
      addView(MeterView(context, BentoFacts.intensity(app.intensity)), LinearLayout.LayoutParams(-1, dp(92)).apply { topMargin = dp(8) })
      addView(headline(BentoFacts.intensityHeadline(app.intensity)).apply { setPadding(0, dp(10), 0, 0) })
    }
  }

  private fun capacityTile(app: CatalogApp): View? {
    if (app.capacity.isEmpty()) return null
    val guests = BentoFacts.guests(app.capacity)
    return card("Capacity").apply {
      if (guests != null) {
        addView(label("🧑".repeat(guests.count.coerceIn(1, 4)), 34f, TEXT).apply { setPadding(0, dp(6), 0, 0) })
        addView(headline("${guests.label} guest${if (guests.count == 1) "" else "s"}"))
      } else addView(headline(BentoFacts.short(app.capacity), 22f))
    }
  }

  private fun positionTile(app: CatalogApp): View? {
    if (app.position.isEmpty()) return null
    val found = BentoFacts.positions(app.position)
    return card("Position").apply {
      val icons = LinearLayout(context).apply { setPadding(0, dp(6), 0, 0) }
      BentoFacts.POSITIONS.forEach { (emoji, name) ->
        icons.addView(label(emoji, 34f, TEXT).apply {
          alpha = if (name in found) 1f else 0.22f
          setPadding(0, 0, dp(6), 0)
        })
      }
      if (found.isNotEmpty()) addView(icons)
      addView(headline(if (found.isEmpty()) BentoFacts.short(app.position) else found.joinToString(" or "), 22f))
    }
  }

  private fun audioTile(app: CatalogApp): View? {
    if (app.audio.isEmpty()) return null
    val (emoji, name) = BentoFacts.audioIcon(app.audio)
    return card("Audio").apply {
      addView(label(emoji, 34f, TEXT).apply { setPadding(0, dp(6), 0, 0) })
      addView(headline(name, 22f))
    }
  }

  private fun consentTile(app: CatalogApp): View? {
    if (app.consent.isEmpty()) return null
    val extra = BentoFacts.needsExtraConsent(app.consent)
    return card(if (extra) "⚠️  Ask first" else "✅  Consent", if (extra) ALERT else WELL).apply {
      addView(headline(if (extra) "Extra consent needed" else "Standard consent", 24f, if (extra) ALERT_TEXT else TEXT))
      if (extra) addView(label(app.consent, 19f, TEXT).apply { setPadding(0, dp(4), 0, 0) })
    }
  }

  private fun ageTile(app: CatalogApp): View? {
    if (app.age.isEmpty()) return null
    val number = BentoFacts.ageNumber(app.age)
    return card("Age").apply {
      addView(headline(when (number) { null -> "Not rated"; "0" -> "All ages"; else -> "$number+" }, 30f))
    }
  }

  private fun iconTile(title: String, text: String, emojis: List<String>): View? {
    if (text.isEmpty()) return null
    return card(title).apply {
      if (emojis.isNotEmpty()) addView(label(emojis.joinToString(" "), 34f, TEXT).apply { setPadding(0, dp(6), 0, 0) })
      addView(headline(BentoFacts.short(text), 21f))
    }
  }

  private fun checklist(app: CatalogApp, part: String, steps: List<String>) {
    if (steps.isEmpty()) {
      tabBody.addView(label("No steps in this guide yet. Add them in Quest Fleet.", 22f, MUTED))
      return
    }
    steps.forEachIndexed { index, step ->
      val done = ticks.isDone(app.id, part, index)
      tabBody.addView(
        CheckBox(context).apply {
          text = step
          textSize = 23f
          setTextColor(if (done) MUTED else TEXT)
          buttonTintList = ColorStateList.valueOf(ACCENT_LIGHT)
          isChecked = done
          background = rounded(if (done) WELL_DONE else WELL, dp(18).toFloat())
          setPadding(dp(10), dp(12), dp(14), dp(12))
          setOnClickListener {
            ticks.toggle(app.id, part, index)
            renderGuide(keepScroll = true)
          }
        },
        matchWrap().apply { bottomMargin = dp(8) },
      )
    }
    if (ticks.any(app.id)) {
      tabBody.addView(
        pillButton("Clear all ticks").apply {
          setOnClickListener {
            ticks.clear(app.id)
            renderGuide(keepScroll = true)
          }
        },
        LinearLayout.LayoutParams(dp(210), dp(52)).apply { topMargin = dp(6) },
      )
    }
  }

  private fun help(app: CatalogApp) {
    if (app.troubleshooting.isEmpty()) {
      tabBody.addView(label("No troubleshooting notes yet. Add them in Quest Fleet.", 22f, MUTED))
      return
    }
    app.troubleshooting.forEach { trouble ->
      tabBody.addView(
        card(trouble.problem).apply {
          if (trouble.fix.isNotEmpty()) addView(label(trouble.fix, 22f, TEXT).apply { setPadding(0, dp(4), 0, 0) })
        },
        matchWrap().apply { bottomMargin = dp(8) },
      )
    }
  }

  private fun renderReadiness(app: CatalogApp) {
    val target = launcher.resolve(app.packageName)
    val left = app.setup.indices.count { !ticks.isDone(app.id, "setup", it) }
    launchButton.isEnabled = target != null
    launchButton.alpha = if (target != null) 1f else 0.45f
    launchButton.text =
      when {
        target != null -> "Launch"
        app.packageName.isEmpty() -> "No headset app"
        else -> "Not installed"
      }
    readiness.text =
      when {
        app.packageName.isEmpty() -> "Runs outside the headset"
        target == null -> "Install it from Quest Fleet"
        app.setup.isEmpty() -> "Ready"
        left > 0 -> "$left setup step${if (left == 1) "" else "s"} left"
        else -> "Set up and ready"
      }
    readiness.setTextColor(if (target != null && left == 0) READY else ALERT_TEXT)
  }

  private fun launchSelected() {
    val app = apps.firstOrNull { it.id == selectedId } ?: return
    val target = launcher.resolve(app.packageName)
    if (target != null) ReturnSession.arm(context, target)
    if (target == null || !launcher.launch(target)) {
      ReturnSession.clear(context)
      readiness.text = "Could not open ${app.title}"
      readiness.setTextColor(WARN)
    }
  }

  private fun label(value: String, size: Float, color: Int, bold: Boolean = false, shadow: Boolean = false) =
    TextView(context).apply {
      text = value
      textSize = size
      setTextColor(color)
      if (bold) typeface = Typeface.DEFAULT_BOLD
      if (shadow) setShadowLayer(10f, 0f, 2f, Color.BLACK)
    }

  private fun vertical() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

  private fun pillButton(value: String) =
    Button(context).apply {
      text = value
      textSize = 18f
      style(this, FIELD, TEXT, 24)
      minHeight = 0
      minimumHeight = 0
      setPadding(dp(8), 0, dp(8), 0)
    }

  private fun style(button: Button, fill: Int, textColor: Int, radius: Int) {
    button.isAllCaps = false
    button.stateListAnimator = null
    button.setTextColor(textColor)
    button.background = rounded(fill, dp(radius).toFloat())
  }

  private fun rounded(color: Int, radius: Float, ring: Int? = null): GradientDrawable =
    GradientDrawable().apply {
      setColor(color)
      cornerRadius = radius
      if (ring != null) setStroke(dp(4), ring)
    }

  private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)

  private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

  private companion object {
    const val KEY_ICON_SIZE = "icon_size"
    const val RAIL_WIDTH = 220
    const val PANEL_GAP = 20
    const val BODY_WIDTH = 1462
    const val TILE_GAP = 44
    const val TILE_TEXT_HEIGHT = 88
    const val ICON_MIN = 100
    const val ICON_DEFAULT = 148
    const val ICON_MAX = 256
    const val ICON_STEP = 12
    const val GUIDE_WIDTH = 800
    val TEXT = Color.parseColor("#F7F5FF")
    val MUTED = Color.parseColor("#B3ADCC")
    val MUTED_LIGHT = Color.parseColor("#D5D0E8")
    val GLASS = Color.parseColor("#B8120E22")
    val FIELD = Color.parseColor("#33FFFFFF")
    val CARD = Color.parseColor("#EB120E22")
    val WELL = Color.parseColor("#22FFFFFF")
    val WELL_DONE = Color.parseColor("#14FFFFFF")
    val QUOTE = Color.parseColor("#4D8B5CF6")
    val ALERT = Color.parseColor("#4DF59E0B")
    val ALERT_TEXT = Color.parseColor("#FCD34D")
    val PLATE = Color.parseColor("#201A36")
    val ACCENT = Color.parseColor("#8B5CF6")
    val ACCENT_LIGHT = Color.parseColor("#C4B5FD")
    val READY = Color.parseColor("#86EFAC")
    val WARN = Color.parseColor("#FCA5A5")
  }
}
