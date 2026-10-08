package voice.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackSettingsTest {

  private val defaults = PlaybackSettings(playbackSpeed = 1.5F, skipSilence = true, gain = 6F)

  @Test
  fun `new books inherit all global settings`() {
    val resolved = book().content.withPlaybackSettings(defaults)
    assertEquals(1.5F, resolved.playbackSpeed)
    assertTrue(resolved.skipSilence)
    assertEquals(6F, resolved.gain)
  }

  @Test
  fun `explicit neutral settings override non neutral global values`() {
    val content = book().content.copy(
      useGlobalPlaybackSpeed = false,
      useGlobalSkipSilence = false,
      useGlobalGain = false,
    )
    val resolved = content.withPlaybackSettings(defaults)
    assertEquals(1F, resolved.playbackSpeed)
    assertFalse(resolved.skipSilence)
    assertEquals(0F, resolved.gain)
  }

  @Test
  fun `each setting inherits independently and can return to global`() {
    val content = book().content.copy(playbackSpeed = 2F, useGlobalPlaybackSpeed = false)
    val resolved = content.withPlaybackSettings(defaults)
    assertEquals(2F, resolved.playbackSpeed)
    assertTrue(resolved.skipSilence)
    assertEquals(6F, resolved.gain)
    assertEquals(1.5F, content.copy(useGlobalPlaybackSpeed = true).withPlaybackSettings(defaults).playbackSpeed)
  }

  @Test
  fun `changing global settings preserves overrides and book progress`() {
    val content = book().content.copy(gain = 3F, useGlobalGain = false)
    val resolved = content.withPlaybackSettings(defaults.copy(playbackSpeed = 2F))
    assertEquals(2F, resolved.playbackSpeed)
    assertEquals(3F, resolved.gain)
    assertEquals(content.positionInChapter, resolved.positionInChapter)
    assertEquals(content.currentChapter, resolved.currentChapter)
    assertTrue(resolved.useGlobalPlaybackSpeed)
    assertFalse(resolved.useGlobalGain)
  }
}
