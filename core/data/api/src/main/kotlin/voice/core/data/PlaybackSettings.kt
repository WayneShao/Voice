package voice.core.data

import kotlinx.serialization.Serializable

@Serializable
public data class PlaybackSettings(
  val playbackSpeed: Float = 1F,
  val skipSilence: Boolean = false,
  val gain: Float = 0F,
)

/** Resolves inherited values for readers without changing which settings the book owns. */
public fun BookContent.withPlaybackSettings(defaults: PlaybackSettings): BookContent = copy(
  playbackSpeed = if (useGlobalPlaybackSpeed) defaults.playbackSpeed else playbackSpeed,
  skipSilence = if (useGlobalSkipSilence) defaults.skipSilence else skipSilence,
  gain = if (useGlobalGain) defaults.gain else gain,
)
