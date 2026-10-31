package nexus.cyberdelic.kiosk

import android.view.KeyEvent
import nexus.cyberdelic.kiosk.ReturnPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherPolicyTest {
  private val home = ReturnPolicy.HOME_ACTIVITY
  private val shell = ReturnPolicy.HOME_PACKAGE
  private fun armed() = ReturnPolicy("nexus.launcher").apply { arm("com.game") }
  private fun started() = armed().apply { observe("com.game", "Main", 1_000) }

  @Test fun `real home after the app brings the launcher back`() {
    assertEquals(Decision.RETURN, started().observe(shell, home, 60_000))
  }

  @Test fun `opening the Meta menu leaves the app alone`() {
    val policy = started()
    assertEquals(Decision.IGNORE, policy.observe(shell, "com.oculus.vrshell.FocusPlaceholderActivity", 60_000))
    assertEquals(Decision.IGNORE, policy.observe("com.oculus.panelapp.library", "com.oculus.navigator.library.app.NavigatorLibraryActivity", 60_100))
    assertEquals(Decision.IGNORE, policy.observe(shell, "com.oculus.panelapp.controlbar.ControlBarActivity", 60_200))
  }

  @Test fun `home is left alone when the launcher opened nothing`() {
    assertEquals(Decision.IGNORE, ReturnPolicy("nexus.launcher").observe(shell, home, 60_000))
  }

  @Test fun `home before the app appears is part of the launch`() {
    assertEquals(Decision.IGNORE, armed().observe(shell, home, 60_000))
  }

  @Test fun `shell windows right after launch do not bounce the wearer back`() {
    assertEquals(Decision.IGNORE, started().observe(shell, home, 1_400))
  }

  @Test fun `the launcher reappearing ends the session`() {
    assertEquals(Decision.FINISHED, started().observe("nexus.launcher", "Nexus", 60_000))
  }

  @Test fun `the launcher's own closing window does not end a starting session`() {
    assertEquals(Decision.IGNORE, armed().observe("nexus.launcher", "Nexus", 1_000))
  }

  @Test fun `both volume buttons together start the hold in either order`() {
    val chord = VolumeChord()
    assertEquals(VolumeChord.Change.NONE, chord.update(KeyEvent.KEYCODE_VOLUME_DOWN, true))
    assertEquals(VolumeChord.Change.BOTH_DOWN, chord.update(KeyEvent.KEYCODE_VOLUME_UP, true))
    assertEquals(VolumeChord.Change.RELEASED, chord.update(KeyEvent.KEYCODE_VOLUME_DOWN, false))
    assertEquals(VolumeChord.Change.NONE, chord.update(KeyEvent.KEYCODE_VOLUME_UP, false))
  }

  @Test fun `one volume button and key repeats never start the hold`() {
    val chord = VolumeChord()
    repeat(5) { assertEquals(VolumeChord.Change.NONE, chord.update(KeyEvent.KEYCODE_VOLUME_UP, true)) }
    assertEquals(VolumeChord.Change.NONE, chord.update(KeyEvent.KEYCODE_VOLUME_UP, false))
    assertEquals(VolumeChord.Change.NONE, chord.update(KeyEvent.KEYCODE_BACK, true))
  }

  @Test fun `follow holds while reading across the panel`() {
    val follow = FollowPolicy()
    follow.reset(0f)
    assertFalse(follow.update(Math.toRadians(30.0).toFloat(), 0.016f))
    assertEquals(0f, follow.yaw, 0f)
  }

  @Test fun `follow glides in front after a clear turn, then rests`() {
    val follow = FollowPolicy()
    follow.reset(0f)
    val turned = Math.toRadians(90.0).toFloat()
    repeat(300) { follow.update(turned, 0.016f) }
    assertEquals(turned, follow.yaw, 0.1f)
    assertFalse(follow.following)
  }

  @Test fun `follow takes the short way past the back of the circle`() {
    val follow = FollowPolicy()
    follow.reset(Math.toRadians(170.0).toFloat())
    assertTrue(follow.update(Math.toRadians(-110.0).toFloat(), 0.016f))
    assertTrue(follow.yaw > Math.toRadians(170.0).toFloat())
  }

  @Test fun `duration reads the real field formats`() {
    assertEquals("11:44", BentoFacts.duration("11:44")?.label)
    assertEquals("10–15", BentoFacts.duration("User-defined; offer 10–15 min for festival flow")?.label)
    assertEquals("15", BentoFacts.duration("15 minutes, plus onboarding and integration")?.label)
    assertNull(BentoFacts.duration("User-defined"))
    assertTrue(BentoFacts.isOpenEnded("User-defined"))
  }

  @Test fun `intensity becomes a level, a range or a word`() {
    assertEquals(BentoFacts.Intensity(4, 4), BentoFacts.intensity("Strong"))
    assertEquals("Gentle to Moderate", BentoFacts.intensityHeadline("Mild–Moderate; guest-controlled"))
    assertEquals("Builds gradually", BentoFacts.intensityHeadline("Progressive"))
  }

  @Test fun `guest placeholders are not a count`() {
    assertEquals("1", BentoFacts.guests("1 guest per headset")?.label)
    assertNull(BentoFacts.guests("[Confirm 2 headsets]"))
  }

  @Test fun `consent and position stay readable`() {
    assertTrue(BentoFacts.needsExtraConsent("Explain stereoscopic immersion"))
    assertFalse(BentoFacts.needsExtraConsent("Standard showcase consent"))
    assertEquals(listOf("Seated", "Lying"), BentoFacts.positions("Seated or reclined"))
    assertTrue(BentoFacts.short("Ask about a very long sentence that keeps going").endsWith("…"))
  }
}
