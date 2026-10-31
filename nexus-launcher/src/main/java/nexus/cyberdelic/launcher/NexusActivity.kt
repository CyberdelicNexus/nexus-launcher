package nexus.cyberdelic.launcher

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.SpatialSDKExperimentalAPI
import com.meta.spatial.core.Vector2
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.AlphaMode
import com.meta.spatial.runtime.PanelConfigOptions
import com.meta.spatial.runtime.PanelShapeLayerBlendType
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.CylinderShapeOptions
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.LayoutXMLPanelRegistration
import com.meta.spatial.toolkit.PanelDimensions
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelRenderMode
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelRenderOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.LocomotionControls
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.vr.VrInputSystemType

/** Lets the room show through everything the Views leave unpainted. */
private class TransparentStyle : PanelStyleOptions(R.style.AppTheme) {
  override fun applyTo(options: PanelConfigOptions) {
    super.applyTo(options)
    options.enableTransparent = true
    options.alphaMode = AlphaMode.TRANSLUCENT
  }
}

/** Immersive host: one curved panel that keeps itself in front of the wearer. */
class NexusActivity : AppSystemActivity() {
  private val ui by lazy { NexusUi(this, CatalogStore(this), AppLauncher(this), GuideTicks(this), ::togglePassthrough, ::changeDistance) }
  private val follow = FollowPolicy()
  private var panelEntity: Entity? = null
  private var sceneReady = false
  private var lastTickMs = 0L

  private val preferences by lazy { getSharedPreferences("nexus_view", Context.MODE_PRIVATE) }
  private var passthrough: Boolean
    get() = preferences.getBoolean(KEY_PASSTHROUGH, true)
    set(value) = preferences.edit().putBoolean(KEY_PASSTHROUGH, value).apply()

  private var distanceStep: Int
    get() = preferences.getInt(KEY_DISTANCE, DEFAULT_DISTANCE_STEP).coerceIn(0, MAX_DISTANCE_STEP)
    set(value) = preferences.edit().putInt(KEY_DISTANCE, value).apply()

  override fun registerFeatures(): List<SpatialFeature> =
    listOf(VRFeature(this, LocomotionControls.Right, false, VrInputSystemType.INTERACTION_SDK))

  override fun registerPanels(): List<PanelRegistration> = listOf(
    LayoutXMLPanelRegistration(
      R.id.nexus_panel,
      layoutIdCreator = { R.layout.nexus_panel },
      settingsCreator = {
        UIPanelSettings(
          shape = CylinderShapeOptions(radius = RADIUS_METERS, width = WIDTH_METERS, height = HEIGHT_METERS),
          display = DpPerMeterDisplayOptions(dpPerMeter = DP_PER_METER),
          style = TransparentStyle(),
          rendering = UIPanelRenderOptions(PanelRenderMode.Layer(PanelShapeLayerBlendType.ALPHA_BLEND)),
        )
      },
      panelSetupWithRootView = { root, _, _ ->
        ui.bind(root)
        ui.showViewMode(passthrough)
        ui.showDistance(distanceStep)
        ui.reload()
      },
    ),
  )

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // The route Rusty Kiosk uses to reach the Quest system keyboard from a panel EditText.
    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
  }

  @OptIn(SpatialSDKExperimentalAPI::class)
  override fun onSceneReady() {
    super.onSceneReady()
    if (panelEntity != null) return
    sceneReady = true
    scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
    scene.enablePassthrough(passthrough)
    follow.reset(viewerYaw() ?: Math.PI.toFloat())
    panelEntity = Entity.createPanelEntity(
      R.id.nexus_panel,
      Transform(panelPose()),
      PanelDimensions(Vector2(WIDTH_METERS, HEIGHT_METERS)),
      Scale(Vector3(1f, 1f, 1f)),
      Visible(true),
    )
  }

  private fun viewerYaw(): Float? {
    val forward = runCatching { scene.getViewerPose() }.getOrNull()?.forward() ?: return null
    return if (forward.x * forward.x + forward.z * forward.z < 0.01f) null
    else FollowPolicy.yawOf(forward.x, forward.z)
  }

  /** Centred on the wearer at reading height, so the curve wraps evenly around them. */
  private fun panelPose(): Pose {
    val viewer = runCatching { scene.getViewerPose() }.getOrNull()
    val x = FollowPolicy.forwardX(follow.yaw)
    val z = FollowPolicy.forwardZ(follow.yaw)
    return Pose(
      Vector3(
        (viewer?.t?.x ?: 0f) + x * pushMeters(),
        ((viewer?.t?.y ?: 1.5f) - 0.05f).coerceIn(0.9f, 2.1f),
        (viewer?.t?.z ?: 0f) + z * pushMeters(),
      ),
      Quaternion.fromDirection(Vector3(x, 0f, z), Vector3(0f, 1f, 0f)),
    )
  }

  private fun pushMeters() = distanceStep * STEP_METERS

  private fun changeDistance(steps: Int) {
    distanceStep = (distanceStep + steps).coerceIn(0, MAX_DISTANCE_STEP)
    ui.showDistance(distanceStep)
    panelEntity?.setComponent(Transform(panelPose()))
  }

  override fun onSceneTick() {
    super.onSceneTick()
    val entity = panelEntity ?: return
    val now = SystemClock.elapsedRealtime()
    val seconds = ((now - lastTickMs) / 1000f).coerceIn(0f, 0.1f)
    lastTickMs = now
    val yaw = viewerYaw() ?: return
    if (follow.update(yaw, seconds)) entity.setComponent(Transform(panelPose()))
  }

  override fun onResume() {
    super.onResume()
    CatalogEvents.onChanged = { runOnUiThread { ui.reload() } }
    // Installed apps may have changed while another app was in front.
    ui.reload()
    // Coming back from an experience, the wearer may be facing anywhere.
    if (sceneReady) {
      viewerYaw()?.let(follow::reset)
      panelEntity?.setComponent(Transform(panelPose()))
    }
  }

  override fun onPause() {
    CatalogEvents.onChanged = null
    super.onPause()
  }

  private fun togglePassthrough() {
    passthrough = !passthrough
    if (sceneReady) scene.enablePassthrough(passthrough)
    ui.showViewMode(passthrough)
  }

  override fun onSpatialShutdown() {
    panelEntity?.destroy()
    panelEntity = null
    super.onSpatialShutdown()
  }

  private companion object {
    const val KEY_PASSTHROUGH = "passthrough"
    const val KEY_DISTANCE = "distance_step"
    // The same layout, physically smaller and on a tighter curve.
    const val RADIUS_METERS = 1.25f
    const val WIDTH_METERS = 2.1875f
    const val HEIGHT_METERS = 0.875f
    const val DP_PER_METER = 800f
    // Each step moves the cylinder forward. Step 0 is the nearest allowed.
    const val STEP_METERS = 0.15f
    const val DEFAULT_DISTANCE_STEP = 3
    const val MAX_DISTANCE_STEP = 10
  }
}
