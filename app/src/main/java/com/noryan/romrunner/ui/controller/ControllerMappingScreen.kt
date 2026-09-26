package com.noryan.romrunner.ui.controller

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.noryan.romrunner.data.input.ControllerMapping
import com.noryan.romrunner.data.input.InputCaptureController
import com.noryan.romrunner.data.input.InputGroup
import com.noryan.romrunner.data.input.PhysicalBinding
import com.noryan.romrunner.data.input.StandardInput
import com.noryan.romrunner.data.input.StickBinding
import com.noryan.romrunner.ui.components.glowColor
import com.noryan.romrunner.ui.components.glowShadow
import com.noryan.romrunner.ui.components.rememberFocusInteractionSource

/**
 * Lets the user remap any of the 18 standard console inputs to a physical button/axis on their
 * controller. Capture ("press the input now") is driven by [InputCaptureController], fed from
 * MainActivity's dispatchKeyEvent/dispatchGenericMotionEvent overrides — this screen never reads
 * raw input itself.
 *
 * Reused for both RomRunner's single global mapping (Settings' own "Controller Mapping" row) and
 * a single platform's override (the "Map Controller" row shown under a platform's own "Use global
 * controller mapping" toggle when it's off) — [initialMapping]/[onSave] parameterize which one
 * this instance reads and writes; the screen itself has no opinion on that. "Reset to AYN Thor
 * Defaults" goes through the same [onSave] path as any other edit (not a separate callback), so it
 * always writes the literal default values rather than, for a platform override, ambiguously
 * deferring back to whatever the global mapping happens to be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerMappingScreen(
    title: String,
    initialMapping: ControllerMapping,
    onSave: (ControllerMapping) -> Unit,
    onDone: () -> Unit,
    showDuskLightNote: Boolean = true
) {
    var mapping by remember(initialMapping) { mutableStateOf(initialMapping) }
    var capturingSlot by remember { mutableStateOf<StandardInput?>(null) }

    fun startCapture(slot: StandardInput) {
        capturingSlot = slot
        InputCaptureController.beginCapture(slot)
    }

    fun cancelCapture() {
        capturingSlot = null
        InputCaptureController.cancelCapture()
    }

    DisposableEffect(Unit) {
        onDispose { InputCaptureController.cancelCapture() }
    }

    LaunchedEffect(Unit) {
        InputCaptureController.keyResult.collect { key ->
            val slot = capturingSlot ?: return@collect
            mapping = mapping.copy(buttons = mapping.buttons + (slot to key))
            onSave(mapping)
            capturingSlot = null
        }
    }
    LaunchedEffect(Unit) {
        InputCaptureController.axisResult.collect { axis ->
            val slot = capturingSlot ?: return@collect
            mapping = mapping.copy(buttons = mapping.buttons + (slot to axis))
            onSave(mapping)
            capturingSlot = null
        }
    }
    LaunchedEffect(Unit) {
        InputCaptureController.stickResult.collect { stick ->
            when (capturingSlot) {
                StandardInput.LEFT_STICK -> mapping = mapping.copy(leftStick = stick)
                StandardInput.RIGHT_STICK -> mapping = mapping.copy(rightStick = stick)
                else -> return@collect
            }
            onSave(mapping)
            capturingSlot = null
        }
    }

    BackHandler(enabled = capturingSlot != null) { cancelCapture() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                InputGroup.entries.forEach { group ->
                    item { SectionHeader(group.heading) }
                    items(StandardInput.entries.filter { it.group == group }) { slot ->
                        MappingRow(
                            label = slot.label,
                            valueLabel = labelFor(slot, mapping),
                            onRemap = { startCapture(slot) }
                        )
                    }
                }
                if (showDuskLightNote) {
                    item {
                        SectionHeader("Twilight Princess")
                        DisabledRow(
                            label = "Twilight Princess (DuskLight)",
                            note = "Not yet supported — uses default controls"
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = {
                            mapping = ControllerMapping.AYN_THOR_DEFAULT
                            onSave(mapping)
                        }) { Text("Reset to AYN Thor Defaults") }
                    }
                }
            }

            capturingSlot?.let { slot ->
                CaptureOverlay(slotLabel = slot.label, onCancel = ::cancelCapture)
            }
        }
    }
}

@Composable
private fun SectionHeader(heading: String) {
    Text(
        heading.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun MappingRow(label: String, valueLabel: String, onRemap: () -> Unit) {
    val interactionSource = rememberFocusInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onRemap)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = LocalTextStyle.current.copy(shadow = interactionSource.glowShadow()),
            color = interactionSource.glowColor(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f)
        )
        Text(
            valueLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DisabledRow(label: String, note: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CaptureOverlay(slotLabel: String, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(indication = null, interactionSource = rememberFocusInteractionSource()) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Press or move the input for", style = MaterialTheme.typography.bodyLarge)
            Text(slotLabel, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private fun labelFor(slot: StandardInput, mapping: ControllerMapping): String = when (slot) {
    StandardInput.LEFT_STICK -> formatStick(mapping.leftStick)
    StandardInput.RIGHT_STICK -> formatStick(mapping.rightStick)
    else -> mapping.buttons[slot]?.let { formatBinding(it) } ?: "Not set"
}

private fun formatBinding(binding: PhysicalBinding): String = when (binding) {
    is PhysicalBinding.Key -> formatKeyCode(binding.keyCode)
    is PhysicalBinding.Axis -> "${formatAxis(binding.axis)} ${if (binding.positiveDirection) "+" else "-"}"
}

private fun formatStick(stick: StickBinding): String =
    "${formatAxis(stick.xAxis)} / ${formatAxis(stick.yAxis)}"

private fun formatKeyCode(keyCode: Int): String =
    KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')

private fun formatAxis(axis: Int): String =
    MotionEvent.axisToString(axis).removePrefix("AXIS_")
