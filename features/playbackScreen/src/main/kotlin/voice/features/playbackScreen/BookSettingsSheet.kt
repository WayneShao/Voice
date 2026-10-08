@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package voice.features.playbackScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import voice.core.data.BookContent
import voice.core.playback.misc.VolumeGain
import java.text.DecimalFormat
import kotlin.math.roundToInt
import voice.core.strings.R as StringsR

@Composable
internal fun BookSettingsSheet(
  content: BookContent,
  onSpeedChange: (Float) -> Unit,
  onSkipSilenceChange: (Boolean) -> Unit,
  onGainChange: (Float) -> Unit,
  onSpeedInheritedChange: (Boolean) -> Unit,
  onSkipSilenceInheritedChange: (Boolean) -> Unit,
  onGainInheritedChange: (Boolean) -> Unit,
  onResetAll: () -> Unit,
  onDismiss: () -> Unit,
) {
  val format = remember { DecimalFormat("0.0#") }
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberBottomSheetState(initialValue = Hidden, enabledValues = setOf(Hidden, Expanded)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(StringsR.string.book_playback_settings), style = MaterialTheme.typography.headlineSmall)
      val speedTitle = stringResource(StringsR.string.playback_speed_title)
      InheritanceRow(speedTitle, content.useGlobalPlaybackSpeed, onSpeedInheritedChange)
      Text(format.format(content.playbackSpeed) + "×")
      BookSettingSlider(
        title = speedTitle,
        value = content.playbackSpeed,
        range = 0.5F..3.5F,
        enabled = !content.useGlobalPlaybackSpeed,
        onChange = { onSpeedChange((it / 0.05F).roundToInt() * 0.05F) },
      )
      val silenceTitle = stringResource(StringsR.string.playback_setting_skip_silence)
      InheritanceRow(silenceTitle, content.useGlobalSkipSilence, onSkipSilenceInheritedChange)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(silenceTitle, Modifier.weight(1F))
        Switch(
          checked = content.skipSilence,
          onCheckedChange = onSkipSilenceChange,
          enabled = !content.useGlobalSkipSilence,
          modifier = Modifier.semantics { contentDescription = silenceTitle },
        )
      }
      val gainTitle = stringResource(StringsR.string.playback_option_volume_boost)
      InheritanceRow(gainTitle, content.useGlobalGain, onGainInheritedChange)
      Text(stringResource(StringsR.string.playback_setting_gain_value, format.format(content.gain)))
      BookSettingSlider(
        title = gainTitle,
        value = content.gain,
        range = 0F..VolumeGain.MAX_GAIN.value,
        enabled = !content.useGlobalGain,
        onChange = onGainChange,
      )
      TextButton(
        onClick = onResetAll,
        enabled = !content.useGlobalPlaybackSpeed || !content.useGlobalSkipSilence || !content.useGlobalGain,
      ) {
        Text(stringResource(StringsR.string.playback_setting_reset_all))
      }
    }
  }
}

@Composable
private fun InheritanceRow(
  title: String,
  inherited: Boolean,
  onInheritedChange: (Boolean) -> Unit,
) {
  Column {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(
      stringResource(if (inherited) StringsR.string.playback_setting_inherited else StringsR.string.playback_setting_overridden),
      style = MaterialTheme.typography.bodyMedium,
    )
    val action =
      stringResource(if (inherited) StringsR.string.playback_setting_book_override else StringsR.string.playback_setting_follow_global)
    TextButton(
      onClick = { onInheritedChange(!inherited) },
      modifier = Modifier.semantics { contentDescription = "$title: $action" },
    ) {
      Text(action)
    }
  }
}

@Composable
private fun BookSettingSlider(
  title: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  enabled: Boolean,
  onChange: (Float) -> Unit,
) {
  val state = rememberSliderState(value = value, trackRange = range)
  SideEffect { state.value = value }
  Slider(
    state = state,
    enabled = enabled,
    onValueChange = {
      state.value = it
      onChange(it)
    },
    modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
  )
}
