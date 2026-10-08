@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.settings.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import voice.core.data.PlaybackSettings
import voice.core.playback.misc.VolumeGain
import java.text.DecimalFormat
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

@Composable
internal fun GlobalPlaybackSection(
  settings: PlaybackSettings,
  onSpeedChange: (Float) -> Unit,
  onSkipSilenceChange: (Boolean) -> Unit,
  onGainChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val format = remember { DecimalFormat("0.0#") }
  SettingsIsland(
    title = stringResource(StringsR.string.global_playback_settings),
    containerColor = MaterialTheme.colorScheme.secondaryContainer,
    modifier = modifier,
  ) {
    Column(Modifier.padding(horizontal = IslandContentPadding)) {
      Text(stringResource(StringsR.string.global_playback_settings_summary))
      GlobalPlaybackSlider(
        title = stringResource(StringsR.string.playback_speed_title),
        value = settings.playbackSpeed,
        formatted = format.format(settings.playbackSpeed) + "×",
        range = 0.5F..3.5F,
        onChange = { onSpeedChange((it / 0.05F).roundToInt() * 0.05F) },
      )
      val skipSilenceTitle = stringResource(StringsR.string.playback_setting_skip_silence)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(skipSilenceTitle, Modifier.weight(1F))
        Switch(
          checked = settings.skipSilence,
          onCheckedChange = onSkipSilenceChange,
          modifier = Modifier.semantics { contentDescription = skipSilenceTitle },
        )
      }
      GlobalPlaybackSlider(
        title = stringResource(StringsR.string.playback_option_volume_boost),
        value = settings.gain,
        formatted = stringResource(StringsR.string.playback_setting_gain_value, format.format(settings.gain)),
        range = 0F..VolumeGain.MAX_GAIN.value,
        onChange = onGainChange,
      )
    }
  }
}

@Composable
private fun GlobalPlaybackSlider(
  title: String,
  value: Float,
  formatted: String,
  range: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
) {
  Column {
    Text("$title: $formatted", style = MaterialTheme.typography.titleMedium)
    val state = rememberSliderState(value = value, trackRange = range)
    SideEffect { state.value = value }
    Slider(
      state = state,
      onValueChange = {
        state.value = it
        onChange(it)
      },
      modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
    )
  }
}
