package com.trueblack.oled.ui

import android.app.StatusBarManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trueblack.oled.AccessMethod
import com.trueblack.oled.AccessState
import com.trueblack.oled.ColorMath
import com.trueblack.oled.Engine
import com.trueblack.oled.Preset
import com.trueblack.oled.R
import com.trueblack.oled.Settings
import com.trueblack.oled.TrueBlackTileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_URL = "https://shizuku.rikka.app/download/"

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val settings by Engine.settings.collectAsStateWithLifecycle()
    val engine by Engine.state.collectAsStateWithLifecycle()
    val tones = remember(settings.threshold, settings.enabled) { tonesFor(settings.threshold, settings.enabled) }

    val msgNoAccess = stringResource(R.string.err_no_access)
    val msgFailed = stringResource(R.string.err_failed)
    val msgPermissionDenied = stringResource(R.string.err_permission_denied)

    fun haptic() {
        if (!settings.haptics) return
        val c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP
        view.performHapticFeedback(c)
    }

    fun report(result: Result<Unit>) {
        result.exceptionOrNull()?.let { e ->
            val text = if (e is Engine.NoAccessException) msgNoAccess
            else msgFailed.format(e.message ?: e.javaClass.simpleName)
            scope.launch { snackbar.showSnackbar(text) }
        }
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Paper,
                    contentColor = Ink,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(36.dp))

            // ---- header
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = Paper)
            Spacer(Modifier.height(6.dp))
            Text(statusLine(settings, engine.access), style = MaterialTheme.typography.bodyLarge, color = tones.mute)

            Spacer(Modifier.height(28.dp))

            // ---- hero toggle
            HeroToggle(
                on = settings.enabled,
                busy = engine.busy,
                tones = tones,
                onToggle = {
                    haptic()
                    scope.launch { report(Engine.setEnabled(!settings.enabled)) }
                }
            )

            Spacer(Modifier.height(40.dp))

            // ---- profile
            SectionLabel(stringResource(R.string.section_profile), tones)
            Spacer(Modifier.height(12.dp))
            PresetRow(
                selected = settings.preset,
                tones = tones,
                onSelect = { p ->
                    haptic()
                    scope.launch { report(Engine.applyPreset(p)) }
                }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = presetDescription(settings),
                style = MaterialTheme.typography.bodySmall,
                color = tones.faint
            )

            Spacer(Modifier.height(36.dp))

            // ---- calibration
            SectionLabel(stringResource(R.string.section_calibration), tones)
            Spacer(Modifier.height(8.dp))
            CalibrationBlock(settings = settings, tones = tones, onError = ::report)

            Spacer(Modifier.height(36.dp))

            // ---- behaviour
            SectionLabel(stringResource(R.string.section_behavior), tones)
            Spacer(Modifier.height(4.dp))
            TileRow(tones)
            Divider(tones)
            SwitchRow(
                title = stringResource(R.string.opt_boot),
                subtitle = stringResource(R.string.opt_boot_sub),
                checked = settings.restoreOnBoot,
                tones = tones,
            ) { v -> Engine.updateOptions { it.copy(restoreOnBoot = v) } }
            Divider(tones)
            SwitchRow(
                title = stringResource(R.string.opt_keep),
                subtitle = stringResource(R.string.opt_keep_sub),
                checked = settings.keepActive,
                tones = tones,
            ) { v -> Engine.updateOptions { it.copy(keepActive = v) } }
            Divider(tones)
            SwitchRow(
                title = stringResource(R.string.opt_dark),
                subtitle = stringResource(R.string.opt_dark_sub),
                checked = settings.forceDarkTheme,
                tones = tones,
            ) { v -> Engine.updateOptions { it.copy(forceDarkTheme = v) } }
            Divider(tones)
            SwitchRow(
                title = stringResource(R.string.opt_haptics),
                subtitle = null,
                checked = settings.haptics,
                tones = tones,
            ) { v -> Engine.updateOptions { it.copy(haptics = v) } }

            Spacer(Modifier.height(36.dp))

            // ---- access
            SectionLabel(stringResource(R.string.section_access), tones)
            Spacer(Modifier.height(4.dp))
            AccessRow(
                access = engine.access,
                tones = tones,
                onGrant = {
                    Engine.requestShizukuPermission { granted ->
                        if (!granted) scope.launch { snackbar.showSnackbar(msgPermissionDenied) }
                    }
                }
            )
            if (engine.access.method == AccessMethod.NONE) {
                Divider(tones)
                SetupGuide(access = engine.access, tones = tones, onGrant = {
                    Engine.requestShizukuPermission { granted ->
                        if (!granted) scope.launch { snackbar.showSnackbar(msgPermissionDenied) }
                    }
                })
                Divider(tones)
                AdbRow(settings, tones)
            }

            Spacer(Modifier.height(40.dp))
            Text(
                stringResource(R.string.footer),
                style = MaterialTheme.typography.bodySmall,
                color = tones.faint
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------ pieces

@Composable
private fun statusLine(s: Settings, access: AccessState): String {
    return when {
        access.method == AccessMethod.NONE && !s.enabled -> stringResource(R.string.state_needs_access)
        s.enabled -> stringResource(R.string.state_active_fmt, presetName(s.preset))
        else -> stringResource(R.string.state_off)
    }
}

@Composable
private fun presetName(p: Preset?): String = when (p) {
    Preset.STANDARD -> stringResource(R.string.preset_standard)
    Preset.DEEP -> stringResource(R.string.preset_deep)
    Preset.VIVID -> stringResource(R.string.preset_vivid)
    Preset.SUBTLE -> stringResource(R.string.preset_subtle)
    null -> stringResource(R.string.preset_custom)
}

@Composable
private fun presetDescription(s: Settings): String = when (s.preset) {
    Preset.STANDARD -> stringResource(R.string.preset_standard_desc)
    Preset.DEEP -> stringResource(R.string.preset_deep_desc)
    Preset.VIVID -> stringResource(R.string.preset_vivid_desc)
    Preset.SUBTLE -> stringResource(R.string.preset_subtle_desc)
    null -> stringResource(
        R.string.preset_custom_desc,
        ColorMath.percent(s.threshold),
        String.format(Locale.getDefault(), "%.2f", s.saturation)
    )
}

@Composable
private fun SectionLabel(text: String, tones: Tones) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = tones.mute)
}

@Composable
private fun Divider(tones: Tones) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(tones.hair))
}

@Composable
private fun HeroToggle(on: Boolean, busy: Boolean, tones: Tones, onToggle: () -> Unit) {
    val bg by animateColorAsState(if (on) Paper else Ink, tween(220), label = "heroBg")
    val fg by animateColorAsState(if (on) Ink else Paper, tween(220), label = "heroFg")
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, if (on) Paper else tones.hair, shape)
            .clickable(enabled = !busy, onClick = onToggle)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (on) stringResource(R.string.hero_on) else stringResource(R.string.hero_off),
                style = MaterialTheme.typography.titleLarge,
                color = fg
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (on) stringResource(R.string.hero_on_sub) else stringResource(R.string.hero_off_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) Color(0xFF5A5A5A) else tones.mute
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = fg, strokeWidth = 2.dp)
        } else {
            // Pixel-off glyph: a ring when off, a solid disc when on.
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (on) Ink else Color.Transparent)
                    .border(2.dp, fg, CircleShape)
            )
        }
    }
}

@Composable
private fun PresetRow(selected: Preset?, tones: Tones, onSelect: (Preset) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Preset.entries.forEach { p ->
            val isSel = p == selected
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(shape)
                    .background(if (isSel) Paper else Ink)
                    .border(1.dp, if (isSel) Paper else tones.hair, shape)
                    .clickable { onSelect(p) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        presetName(p),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSel) Ink else Paper,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${ColorMath.percent(p.threshold)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSel) Color(0xFF5A5A5A) else tones.mute
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationBlock(settings: Settings, tones: Tones, onError: (Result<Unit>) -> Unit) {
    val scope = rememberCoroutineScope()
    var threshold by remember { mutableFloatStateOf(settings.threshold) }
    var saturation by remember { mutableFloatStateOf(settings.saturation) }
    var pending by remember { mutableStateOf<Job?>(null) }

    // Follow external changes (presets, tile) without fighting an in-progress drag.
    LaunchedEffect(settings.threshold, settings.saturation) {
        threshold = settings.threshold
        saturation = settings.saturation
    }

    // While dragging only the compositor is updated (preview); the value is persisted on release,
    // so the saved settings never snap the slider back mid-gesture.
    fun push(immediate: Boolean) {
        pending?.cancel()
        pending = scope.launch {
            if (immediate) {
                onError(Engine.setCalibration(threshold, saturation))
            } else {
                delay(48)
                Engine.previewCalibration(threshold, saturation)
            }
        }
    }

    val sliderColors = SliderDefaults.colors(
        thumbColor = Paper,
        activeTrackColor = Paper,
        inactiveTrackColor = tones.track,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )

    // Black floor
    ValueHeader(stringResource(R.string.cal_black_floor), "${ColorMath.percent(threshold)}%", tones)
    Slider(
        value = threshold,
        onValueChange = { threshold = it; push(false) },
        onValueChangeFinished = { push(true) },
        valueRange = ColorMath.MIN_THRESHOLD..ColorMath.MAX_THRESHOLD,
        steps = 28,
        colors = sliderColors
    )
    GreyRamp(threshold = threshold, tones = tones)
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.cal_ramp_hint),
        style = MaterialTheme.typography.bodySmall,
        color = tones.faint
    )

    Spacer(Modifier.height(22.dp))

    // Vibrancy
    ValueHeader(
        stringResource(R.string.cal_vibrancy),
        String.format(Locale.getDefault(), "%.2f×", saturation),
        tones
    )
    Slider(
        value = saturation,
        onValueChange = { saturation = it; push(false) },
        onValueChangeFinished = { push(true) },
        valueRange = ColorMath.MIN_SATURATION..ColorMath.MAX_SATURATION,
        steps = 15,
        colors = sliderColors
    )
}

@Composable
private fun ValueHeader(title: String, value: String, tones: Tones) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = Paper, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = tones.mute)
    }
}

/**
 * A true grey ramp 0 → 30 %. It is deliberately NOT lifted above the floor: when True Black is active,
 * the part that has collapsed to pure black is exactly what the compositor is doing to the whole screen.
 */
@Composable
private fun GreyRamp(threshold: Float, tones: Tones) {
    val max = ColorMath.MAX_THRESHOLD
    val shape = RoundedCornerShape(6.dp)
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(Color(0f, 0f, 0f), Color(max, max, max))))
                .border(1.dp, tones.hair, shape)
        )
        Spacer(Modifier.height(6.dp))
        // marker under the ramp at the current floor
        val frac = (threshold / max).coerceIn(0f, 1f)
        Row(Modifier.fillMaxWidth()) {
            if (frac > 0f) Spacer(Modifier.weight(frac))
            Box(Modifier.width(2.dp).height(8.dp).background(Paper))
            if (frac < 1f) Spacer(Modifier.weight(1f - frac))
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    tones: Tones,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Paper)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = tones.mute)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Paper,
                checkedBorderColor = Paper,
                uncheckedThumbColor = tones.mute,
                uncheckedTrackColor = Ink,
                uncheckedBorderColor = tones.track,
            )
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, tones: Tones, action: String?, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Paper)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = tones.mute)
        }
        if (action != null) {
            Spacer(Modifier.width(16.dp))
            PillButton(action, onAction)
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Paper)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
private fun TileRow(tones: Tones) {
    val context = LocalContext.current
    val canRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ActionRow(
        title = stringResource(R.string.opt_tile),
        subtitle = if (canRequest) stringResource(R.string.opt_tile_sub) else stringResource(R.string.opt_tile_sub_manual),
        tones = tones,
        action = if (canRequest) stringResource(R.string.action_add) else null,
        onAction = { if (canRequest) requestTile(context) }
    )
}

private fun requestTile(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(context, TrueBlackTileService::class.java),
        context.getString(R.string.app_name),
        Icon.createWithResource(context, R.drawable.ic_tile),
        context.mainExecutor
    ) { }
}

@Composable
private fun AccessRow(access: AccessState, tones: Tones, onGrant: () -> Unit) {
    val context = LocalContext.current
    val shizukuInstalled = remember {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) != null
    }
    when (access) {
        AccessState.Shizuku -> ActionRow(
            stringResource(R.string.access_shizuku), stringResource(R.string.access_connected), tones, null
        ) {}
        AccessState.Root -> ActionRow(
            stringResource(R.string.access_root), stringResource(R.string.access_root_sub), tones, null
        ) {}
        AccessState.ShizukuNeedsPermission -> ActionRow(
            stringResource(R.string.access_shizuku), stringResource(R.string.access_needs_permission), tones,
            stringResource(R.string.action_grant), onGrant
        )
        AccessState.Unavailable -> ActionRow(
            stringResource(R.string.access_none),
            if (shizukuInstalled) stringResource(R.string.access_none_installed) else stringResource(R.string.access_none_sub),
            tones,
            if (shizukuInstalled) stringResource(R.string.action_open) else stringResource(R.string.action_get)
        ) {
            if (shizukuInstalled) openShizuku(context) else getShizuku(context)
        }
    }
}

/** Manual fallback: the exact compositor command, ready for `adb shell`. */
@Composable
private fun AdbRow(settings: Settings, tones: Tones) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val onCmd = remember(settings.threshold, settings.saturation) {
        "adb shell " + ColorMath.serviceCallArgs(ColorMath.matrix(settings.threshold, settings.saturation)).joinToString(" ")
    }
    val offCmd = "adb shell " + ColorMath.serviceCallArgs(null).joinToString(" ")

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.adb_title), style = MaterialTheme.typography.bodyLarge, color = Paper)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.adb_sub), style = MaterialTheme.typography.bodyMedium, color = tones.mute)
            }
            Text(if (expanded) "–" else "+", style = MaterialTheme.typography.titleLarge, color = tones.mute)
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                CodeBlock(stringResource(R.string.adb_on), onCmd, tones) { copy(context, onCmd) }
                Spacer(Modifier.height(10.dp))
                CodeBlock(stringResource(R.string.adb_off), offCmd, tones) { copy(context, offCmd) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CodeBlock(label: String, code: String, tones: Tones, onCopy: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tones.well)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tones.mute, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.action_copy),
                style = MaterialTheme.typography.labelLarge,
                color = Paper,
                modifier = Modifier.clickable(onClick = onCopy).padding(4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(code, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = Paper)
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("True Black", text))
}

private fun openShizuku(context: Context) {
    val i = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return
    runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun getShizuku(context: Context) {
    val play = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(play) }.isSuccess) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openDeveloperOptions(context: Context) {
    val dev = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(dev) }.isSuccess) return
    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * One-time setup, as a numbered list with the right system screen one tap away at each step.
 * Shown only while the app has no way to reach the compositor.
 */
@Composable
private fun SetupGuide(access: AccessState, tones: Tones, onGrant: () -> Unit) {
    val context = LocalContext.current
    val shizukuInstalled = remember(access) {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) != null
    }
    val current = when {
        !shizukuInstalled -> 1
        access is AccessState.ShizukuNeedsPermission -> 5
        else -> 2
    }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.bodyLarge, color = Paper)
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.setup_sub), style = MaterialTheme.typography.bodyMedium, color = tones.mute)
        Spacer(Modifier.height(14.dp))
        SetupStep(1, stringResource(R.string.setup_1), current, tones, stringResource(R.string.action_get)) { getShizuku(context) }
        SetupStep(2, stringResource(R.string.setup_2), current, tones, stringResource(R.string.action_open)) { openDeveloperOptions(context) }
        SetupStep(3, stringResource(R.string.setup_3), current, tones, null) {}
        SetupStep(4, stringResource(R.string.setup_4), current, tones, stringResource(R.string.action_open)) { openShizuku(context) }
        SetupStep(5, stringResource(R.string.setup_5), current, tones, stringResource(R.string.action_grant)) { onGrant() }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.setup_after), style = MaterialTheme.typography.bodySmall, color = tones.faint)
    }
}

@Composable
private fun SetupStep(n: Int, text: String, current: Int, tones: Tones, action: String?, onAction: () -> Unit) {
    val done = n < current
    val active = n == current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (done) Paper else Ink)
                .border(1.dp, if (done || active) Paper else tones.hair, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else n.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (done) Ink else if (active) Paper else tones.mute
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) tones.mute else Paper,
            modifier = Modifier.weight(1f)
        )
        if (action != null && active) {
            Spacer(Modifier.width(12.dp))
            PillButton(action, onAction)
        }
    }
}
