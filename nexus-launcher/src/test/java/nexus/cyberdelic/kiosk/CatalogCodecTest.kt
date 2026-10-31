package nexus.cyberdelic.kiosk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogCodecTest {
  private val valid =
    """{"schema":"nexus.catalog.v1","generated":"2026-11-02T18:30:00Z","extra":1,"apps":[
      {"id":"oblatio","title":"Oblatio","emoji":"🎬","package":"com.example.oblatio","by":"D",
       "setup":["One","Two"],"unknown":true,
       "consent":"Standard","troubleshooting":[{"problem":"No audio","fix":"Check volume"}]},
      {"id":"bed","title":"Haptic Bed"}]}"""

  @Test fun `reads apps in order and tolerates unknown fields`() {
    val catalog = CatalogCodec.parse(valid)
    assertEquals("2026-11-02T18:30:00Z", catalog.generated)
    assertEquals(listOf("oblatio", "bed"), catalog.apps.map { it.id })
    assertEquals(listOf("One", "Two"), catalog.apps[0].setup)
    assertEquals("", catalog.apps[1].packageName)
    assertEquals("Standard", catalog.apps[0].consent)
    assertEquals(Trouble("No audio", "Check volume"), catalog.apps[0].troubleshooting.single())
  }

  @Test fun `rejects a wrong schema`() {
    assertThrows(IllegalArgumentException::class.java) {
      CatalogCodec.parse("""{"schema":"other","apps":[]}""")
    }
  }

  @Test fun `rejects duplicate ids`() {
    assertThrows(IllegalArgumentException::class.java) {
      CatalogCodec.parse(
        """{"schema":"nexus.catalog.v1","apps":[{"id":"a","title":"A"},{"id":"a","title":"B"}]}""",
      )
    }
  }

  @Test fun `rejects an unsafe package name`() {
    assertThrows(IllegalArgumentException::class.java) {
      CatalogCodec.parse(
        """{"schema":"nexus.catalog.v1","apps":[{"id":"a","title":"A","package":"x; rm -rf"}]}""",
      )
    }
  }

  @Test fun `malformed json fails rather than showing part of a menu`() {
    assertThrows(Exception::class.java) { CatalogCodec.parse("""{"schema":"nexus.catalog.v1","apps":[""") }
  }

  @Test fun `search needs every word and ignores case`() {
    val apps = CatalogCodec.parse(valid).apps
    assertEquals(listOf("bed"), CatalogSearch.filter(apps, "HAPTIC bed").map { it.id })
    assertEquals(2, CatalogSearch.filter(apps, "  ").size)
    assertEquals(0, CatalogSearch.filter(apps, "haptic oblatio").size)
  }

  @Test fun `initials come from the first two words`() {
    assertEquals("HB", CatalogSearch.initials("Haptic Bed Deluxe"))
  }
}
