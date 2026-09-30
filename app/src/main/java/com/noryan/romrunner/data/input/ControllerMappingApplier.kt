package com.noryan.romrunner.data.input

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import info.cemu.cemu.nativeinterface.NativeInput
import java.io.File
import kotlinx.serialization.json.Json
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.impl.dtos.input.ControllerConfigurationDto
import me.magnum.melonds.impl.dtos.input.InputConfigDto
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.model.view.InputBindingSetting

/**
 * Translates one canonical [ControllerMapping] into each embedded core's own already-existing
 * mapping mechanism — never a new mapping system of our own. Each `applyTo*` function is called
 * from that core's own `*EmbeddedLauncher.kt` at the point it used to apply a one-time hardcoded
 * default; these are safe (and intended) to call on every launch, so a live remap actually takes
 * effect without needing a data wipe.
 *
 * Eden (`app/src/full` only, since it's the one flavor-gated core) is NOT here — its applier lives
 * directly in EdenEmbeddedLauncher.kt, which already only exists in that flavor.
 */
object ControllerMappingApplier {

    // ---------------------------------------------------------------------------------------
    // ARMSX2 (PS2)
    // ---------------------------------------------------------------------------------------

    /**
     * Writes directly into ARMSX2's own "ARMSX2" SharedPreferences file under its real
     * `pad.map.<id>` keys (confirmed against armsx2-src's ControllerMappings.kt — player 0's key
     * prefix is empty, matching what PS2EmbeddedLauncher.kt already writes for 4 of these).
     *
     * Deliberately does NOT touch the analog sticks: ARMSX2's only stick-related preferences are
     * `pad.lstick`/`pad.rstick` invert/swap flags — the SAME keys PS2EmbeddedLauncher's own
     * `applyStandingStickPreferencesIfNeeded` uses for its standing right-stick camera-invert
     * default (a real per-game preference, unrelated to controller remapping, tuned after testing
     * Jak 3). Writing to those keys here on every launch from the *default* (non-inverted) canonical
     * stick binding would silently stomp that standing preference back to off on every single
     * launch. Sticks are fixed AYN Thor hardware anyway, so this is a deliberate, narrow scope
     * limit rather than a real loss of remap coverage.
     */
    fun applyToArmsx2(context: Context, mapping: ControllerMapping) {
        val prefs = context.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val b = mapping.buttons

        fun key(id: String, input: StandardInput) {
            val binding = b[input] as? PhysicalBinding.Key ?: return
            editor.putInt("pad.map.$id", binding.keyCode)
        }

        key("dpad_up", StandardInput.DPAD_UP)
        key("dpad_down", StandardInput.DPAD_DOWN)
        key("dpad_left", StandardInput.DPAD_LEFT)
        key("dpad_right", StandardInput.DPAD_RIGHT)
        // PS2 button-name positions: cross=bottom, circle=right, square=left, triangle=top.
        key("cross", StandardInput.FACE_BOTTOM)
        key("circle", StandardInput.FACE_RIGHT)
        key("square", StandardInput.FACE_LEFT)
        key("triangle", StandardInput.FACE_TOP)
        key("l1", StandardInput.L1)
        key("r1", StandardInput.R1)
        key("l2", StandardInput.L2)
        key("r2", StandardInput.R2)
        key("l3", StandardInput.L3)
        key("r3", StandardInput.R3)
        key("select", StandardInput.SELECT)
        key("start", StandardInput.START)

        editor.apply()
    }

    // ---------------------------------------------------------------------------------------
    // Azahar (3DS)
    // ---------------------------------------------------------------------------------------

    /**
     * Writes directly into Azahar's default SharedPreferences (PreferenceManager.getDefaultSharedPreferences —
     * confirmed as InputBindingSetting's own backing store) using the exact key shapes its own
     * private applyDefaultButtonMapping/applyDefaultAxisMapping use internally (read directly from
     * source since those two functions themselves are private and can't be called). Starts by
     * calling the existing public clearAllBindings() first — the same call RomRunner's old one-time
     * default already made — so every write below lands on an empty slate with no stale/duplicate
     * bindings to clean up.
     *
     * 3DS has no L3/R3 (no clickable circle pad) — those two canonical slots are simply skipped for
     * this core.
     */
    fun applyToAzahar(context: Context, mapping: ControllerMapping) {
        InputBindingSetting.clearAllBindings()

        // Equivalent to androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        // (that AndroidX artifact isn't on this module's own classpath) — its default-prefs file
        // name is, and has long been, exactly "<packageName>_preferences".
        val prefs = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val b = mapping.buttons

        fun writeButton(settingKey: String, input: StandardInput, guestButtonCode: Int) {
            val binding = b[input] as? PhysicalBinding.Key ?: return
            val hostKey = "InputMapping_HostAxis_${binding.keyCode}"
            editor.putInt(hostKey, guestButtonCode)
            editor.putString("InputMapping_ReverseMapping_$settingKey", hostKey)
        }

        // Nintendo-layout diamond: A=right, B=bottom, X=top, Y=left — matches the default AYN
        // Thor physical assignment exactly (FACE_RIGHT already defaults to KEYCODE_BUTTON_A).
        writeButton(Settings.KEY_BUTTON_A, StandardInput.FACE_RIGHT, NativeLibrary.ButtonType.BUTTON_A)
        writeButton(Settings.KEY_BUTTON_B, StandardInput.FACE_BOTTOM, NativeLibrary.ButtonType.BUTTON_B)
        writeButton(Settings.KEY_BUTTON_X, StandardInput.FACE_TOP, NativeLibrary.ButtonType.BUTTON_X)
        writeButton(Settings.KEY_BUTTON_Y, StandardInput.FACE_LEFT, NativeLibrary.ButtonType.BUTTON_Y)
        writeButton(Settings.KEY_BUTTON_SELECT, StandardInput.SELECT, NativeLibrary.ButtonType.BUTTON_SELECT)
        writeButton(Settings.KEY_BUTTON_START, StandardInput.START, NativeLibrary.ButtonType.BUTTON_START)
        // 3DS's physical shoulder buttons are Citra's "L"/"R" (guest code TRIGGER_L/TRIGGER_R,
        // despite the name — confirmed via InputBindingSetting.getButtonKey); ZL/ZR are the
        // New3DS's separate second shoulder pair.
        writeButton(Settings.KEY_BUTTON_L, StandardInput.L1, NativeLibrary.ButtonType.TRIGGER_L)
        writeButton(Settings.KEY_BUTTON_R, StandardInput.R1, NativeLibrary.ButtonType.TRIGGER_R)
        writeButton(Settings.KEY_BUTTON_ZL, StandardInput.L2, NativeLibrary.ButtonType.BUTTON_ZL)
        writeButton(Settings.KEY_BUTTON_ZR, StandardInput.R2, NativeLibrary.ButtonType.BUTTON_ZR)
        writeButton(Settings.KEY_BUTTON_UP, StandardInput.DPAD_UP, NativeLibrary.ButtonType.DPAD_UP)
        writeButton(Settings.KEY_BUTTON_DOWN, StandardInput.DPAD_DOWN, NativeLibrary.ButtonType.DPAD_DOWN)
        writeButton(Settings.KEY_BUTTON_LEFT, StandardInput.DPAD_LEFT, NativeLibrary.ButtonType.DPAD_LEFT)
        writeButton(Settings.KEY_BUTTON_RIGHT, StandardInput.DPAD_RIGHT, NativeLibrary.ButtonType.DPAD_RIGHT)

        fun writeAxis(settingKey: String, axis: Int, orientation: Int, guestButton: Int, inverted: Boolean) {
            val axisKey = "InputMapping_HostAxis_$axis"
            editor.putInt("${axisKey}_GuestOrientation", orientation)
            editor.putInt("${axisKey}_GuestButton", guestButton)
            editor.putBoolean("${axisKey}_Inverted", inverted)
            editor.putString("InputMapping_ReverseMapping_${settingKey}_$orientation", axisKey)
        }

        // orientation: 0 = horizontal setting key, 1 = vertical setting key (confirmed from source).
        writeAxis(Settings.KEY_CIRCLEPAD_AXIS_VERTICAL, mapping.leftStick.yAxis, 1, NativeLibrary.ButtonType.STICK_LEFT, mapping.leftStick.invertY)
        writeAxis(Settings.KEY_CIRCLEPAD_AXIS_HORIZONTAL, mapping.leftStick.xAxis, 0, NativeLibrary.ButtonType.STICK_LEFT, mapping.leftStick.invertX)
        writeAxis(Settings.KEY_CSTICK_AXIS_VERTICAL, mapping.rightStick.yAxis, 1, NativeLibrary.ButtonType.STICK_C, mapping.rightStick.invertY)
        writeAxis(Settings.KEY_CSTICK_AXIS_HORIZONTAL, mapping.rightStick.xAxis, 0, NativeLibrary.ButtonType.STICK_C, mapping.rightStick.invertX)

        editor.apply()
    }

    // ---------------------------------------------------------------------------------------
    // PrimeHack (GameCube only — see doc comment)
    // ---------------------------------------------------------------------------------------

    /**
     * Generates a `[GCPad1]` section for GCPadNew.ini from the canonical mapping — the real gap
     * this closes: real GameCube-disc titles (.ciso files) currently get NO default control
     * mapping at all, since the existing bundled odin.ini profile only ever wrote WiimoteNew.ini.
     *
     * Deliberately does NOT touch WiimoteNew.ini's `[Wiimote1]` section. That section (see
     * assets/odin.ini) is a bespoke, heavily-tuned Metroid Prime Trilogy control scheme — camera
     * look, beam/visor switching, a dedicated PrimeHack "Mode" — that doesn't decompose into a
     * simple "which physical button is my Wiimote A button" mapping the way every other core here
     * does (its own button↔physical assignments don't follow a simple position convention at all,
     * e.g. D-Pad/Down is bound to the physical R1 shoulder). Regenerating it from the generic
     * 18-slot schema would silently break beam/visor switching and camera control for the one Wii
     * title this project plays — a real regression, not a hypothetical one. Wii titles keep their
     * existing working scheme, unaffected by Controller Mapping; only GameCube titles are covered
     * here, since there was nothing there to break.
     *
     * Dolphin's ciface `` `Axis N` `` numbering matches Android's MotionEvent.AXIS_* int values
     * directly (confirmed against odin.ini's own real values: Axis 11/14 = AXIS_Z/AXIS_RZ, Axis
     * 15/16 = AXIS_HAT_X/HAT_Y, Axis 22/23 = AXIS_GAS/AXIS_BRAKE), so no translation table is
     * needed for stick axes. Button names are NOT a simple "strip KEYCODE_" derivation (confirmed
     * against Dolphin's real Android.cpp KEYCODE_NAMES table — e.g. THUMBL/THUMBR are named
     * "Button L3"/"Button R3", Select/Start are bare "Select"/"Start" with no "Button" prefix, and
     * D-pad keys are bare "Up"/"Down"/"Left"/"Right") — see [dolphinButtonName].
     */
    fun applyToPrimeHack(configDir: File, mapping: ControllerMapping) {
        val b = mapping.buttons
        val updates = LinkedHashMap<String, String>()

        // RomRunner integration: explicitly (re-)sets Device, matching the exact value Dolphin's
        // own native device-detection already writes for GCPad2/3/4 on this device (the AYN
        // Thor's physical controller reports as "Odin Controller" — this project's one supported
        // device). Needed both going forward and to repair any install where an earlier
        // wholesale-replace of this section (see the mergeIniSection comment below) already
        // dropped it — without a Device line, ciface has no physical controller to bind any of
        // these expressions to, which is why GameCube games were totally unresponsive.
        updates["Device"] = "Android/1/Odin Controller"

        fun buttonLine(key: String, input: StandardInput) {
            val binding = b[input] as? PhysicalBinding.Key ?: return
            val name = dolphinButtonName(binding.keyCode) ?: return
            updates[key] = "`$name`"
        }

        fun resolvedButtonName(input: StandardInput): String? {
            val binding = b[input] as? PhysicalBinding.Key ?: return null
            return dolphinButtonName(binding.keyCode)
        }

        // GameCube's own diamond doesn't map 1:1 onto a generic Xbox-style pad (it has a Z button
        // and no Select), so this is a best-effort position mapping, consistent with how every
        // other core here treats FACE_TOP/BOTTOM/LEFT/RIGHT as physical positions, not letters.
        // RomRunner integration: confirmed on-device with the real controller that A/B landed on
        // the wrong physical buttons with FACE_BOTTOM/FACE_RIGHT swapped from what's below.
        buttonLine("Buttons/A", StandardInput.FACE_RIGHT)
        buttonLine("Buttons/B", StandardInput.FACE_BOTTOM)
        buttonLine("Buttons/X", StandardInput.FACE_LEFT)
        buttonLine("Buttons/Y", StandardInput.FACE_TOP)
        buttonLine("Buttons/Start", StandardInput.START)
        // RomRunner integration: per explicit request, GC's L/R triggers are driven ONLY by the
        // physical shoulder triggers' ANALOG pressure (Triggers/L-Analog and /R-Analog), not a
        // plain digital press (Triggers/L / /R, left unbound here) — some titles (e.g. Mario
        // Sunshine) need a half-pull to register as a distinct, sustained analog value (hover
        // nozzle) from a full pull (a different action), which a digital on/off binding can't
        // represent at all. Dolphin's own MixedTriggers input group (see Core/HW/GCPadEmu.cpp)
        // derives a "fully pressed" digital click automatically once the analog value crosses its
        // own internal threshold, so the digital keys don't need their own binding for a full
        // press to still work.
        //
        // The AYN Thor's physical L2/R2 triggers report as raw Android axes 22/23
        // (AXIS_GAS/AXIS_BRAKE) rather than the more common AXIS_LTRIGGER/AXIS_RTRIGGER —
        // confirmed against odin.ini's own established use of these same two axis numbers for
        // this exact device (see this function's own doc comment above). Axis 22/23 is confirmed;
        // which of the two is physically left vs. right is a best-effort guess (L=23, R=22) not
        // yet verified against the real hardware — swap them here if trigger feel comes out
        // reversed on-device.
        updates["Triggers/L-Analog"] = "`Axis 23+`"
        updates["Triggers/R-Analog"] = "`Axis 22+`"
        // Explicitly clears any digital Triggers/L or /R binding a previous version of this
        // function wrote (`Button L2`/`Button R2`) — a leftover digital binding would still fire
        // on any partial pull, defeating the point of switching to analog-only above.
        updates["Triggers/L"] = ""
        updates["Triggers/R"] = ""
        // GC's Z button has no natural equivalent on a generic pad. Per explicit request, it's
        // bound to EITHER bumper (L1 or R1 — now freed up since the triggers above moved to
        // L2/R2), combined via ciface's `|` (OR) operator so it's reachable from both shoulders.
        val zSources = listOfNotNull(
            resolvedButtonName(StandardInput.L1)?.let { "`$it`" },
            resolvedButtonName(StandardInput.R1)?.let { "`$it`" }
        )
        if (zSources.isNotEmpty()) {
            updates["Buttons/Z"] = zSources.joinToString(" | ")
        }
        buttonLine("D-Pad/Up", StandardInput.DPAD_UP)
        buttonLine("D-Pad/Down", StandardInput.DPAD_DOWN)
        buttonLine("D-Pad/Left", StandardInput.DPAD_LEFT)
        buttonLine("D-Pad/Right", StandardInput.DPAD_RIGHT)

        fun stickLines(prefix: String, stick: StickBinding) {
            updates["$prefix/Up"] = axisExpr(stick.yAxis, positive = stick.invertY)
            updates["$prefix/Down"] = axisExpr(stick.yAxis, positive = !stick.invertY)
            updates["$prefix/Left"] = axisExpr(stick.xAxis, positive = stick.invertX)
            updates["$prefix/Right"] = axisExpr(stick.xAxis, positive = !stick.invertX)
        }
        stickLines("Main Stick", mapping.leftStick)
        stickLines("C-Stick", mapping.rightStick)

        // RomRunner integration: merges these keys into [GCPad1] instead of replacing the whole
        // section (as this used to, via replaceIniSection) — Dolphin's own native device-detection
        // pre-populates this section with `Device = Android/1/Odin Controller` and
        // `PrimeHack/Mode = 1` the first time the app runs (confirmed on-device: GCPad2/3/4 carry
        // the exact same two lines), and wholesale-replacing the section silently discarded them
        // on every launch. Without a Device line, ciface has no physical controller to bind these
        // button/axis expressions to at all — confirmed on-device as the actual cause of GameCube
        // games being completely unresponsive to the real controller (Super Mario Sunshine).
        mergeIniSection(File(configDir, "GCPadNew.ini"), "GCPad1", updates)
    }

    private fun dolphinButtonName(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "Up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_BUTTON_A -> "Button A"
        KeyEvent.KEYCODE_BUTTON_B -> "Button B"
        KeyEvent.KEYCODE_BUTTON_C -> "Button C"
        KeyEvent.KEYCODE_BUTTON_X -> "Button X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Button Y"
        KeyEvent.KEYCODE_BUTTON_Z -> "Button Z"
        KeyEvent.KEYCODE_BUTTON_L1 -> "Button L1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "Button R1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "Button L2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "Button R2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "Button L3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "Button R3"
        KeyEvent.KEYCODE_BUTTON_START -> "Start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
        else -> null
    }

    private fun axisExpr(axis: Int, positive: Boolean): String = "`Axis $axis${if (positive) "+" else "-"}`"

    /** Merges `key = value` pairs from [updates] into `[sectionName]` of [file], creating the
     *  section if needed — every other key already in that section (e.g. a native-init-written
     *  `Device`/`PrimeHack/Mode` line) is left untouched, same merge semantics as
     *  PrimeHackEmbeddedLauncher's own single-key setIniValue, just for several keys at once. */
    private fun mergeIniSection(file: File, sectionName: String, updates: Map<String, String>) {
        val lines = (if (file.exists()) file.readText() else "").lines().toMutableList()
        val sectionHeader = "[$sectionName]"
        var sectionStart = lines.indexOfFirst { it.trim() == sectionHeader }
        if (sectionStart == -1) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add(sectionHeader)
            sectionStart = lines.size - 1
        }
        var sectionEnd = lines.size
        for (i in sectionStart + 1 until lines.size) {
            if (lines[i].trim().startsWith("[")) {
                sectionEnd = i
                break
            }
        }
        val remaining = LinkedHashMap(updates)
        for (i in sectionStart + 1 until sectionEnd) {
            val trimmed = lines[i].trim()
            val matchedKey = remaining.keys.firstOrNull { trimmed.startsWith("$it ") || trimmed.startsWith("$it=") }
            if (matchedKey != null) {
                lines[i] = "$matchedKey = ${remaining.remove(matchedKey)}"
            }
        }
        var insertAt = sectionEnd
        for ((key, value) in remaining) {
            lines.add(insertAt, "$key = $value")
            insertAt++
        }
        file.writeText(lines.joinToString("\n"))
    }

    // ---------------------------------------------------------------------------------------
    // Cemu (Wii U)
    // ---------------------------------------------------------------------------------------

    /**
     * Calls NativeInput.setControllerMapping(...) directly per slot — confirmed accessible the
     * same way InputMapper.kt itself calls it — using the real NativeInput.ProButton/Axis int
     * constants (read directly from NativeInput.kt) rather than the private ProControllerButtons/
     * AxisInputMapping wrapper enums InputMapper.kt uses internally for ITS OWN auto-detect
     * heuristic (a different, unrelated code path from this explicit per-slot write).
     */
    fun applyToCemu(device: InputDevice, mapping: ControllerMapping) {
        val descriptor = device.descriptor
        val name = device.name
        val b = mapping.buttons

        fun setButton(mappingId: Int, input: StandardInput) {
            when (val binding = b[input]) {
                is PhysicalBinding.Key -> NativeInput.setControllerMapping(descriptor, name, 0, mappingId, binding.keyCode)
                is PhysicalBinding.Axis -> {
                    val code = nativeAxisCode(binding.axis, binding.positiveDirection) ?: return
                    NativeInput.setControllerMapping(descriptor, name, 0, mappingId, code)
                }
                null -> {}
            }
        }

        setButton(NativeInput.ProButton.UP, StandardInput.DPAD_UP)
        setButton(NativeInput.ProButton.DOWN, StandardInput.DPAD_DOWN)
        setButton(NativeInput.ProButton.LEFT, StandardInput.DPAD_LEFT)
        setButton(NativeInput.ProButton.RIGHT, StandardInput.DPAD_RIGHT)
        // Wii U Pro Controller diamond matches the Nintendo convention: A=right, B=bottom,
        // X=top, Y=left — same as Azahar's, and matches the AYN Thor default identically.
        setButton(NativeInput.ProButton.A, StandardInput.FACE_RIGHT)
        setButton(NativeInput.ProButton.B, StandardInput.FACE_BOTTOM)
        setButton(NativeInput.ProButton.X, StandardInput.FACE_TOP)
        setButton(NativeInput.ProButton.Y, StandardInput.FACE_LEFT)
        setButton(NativeInput.ProButton.L, StandardInput.L1)
        setButton(NativeInput.ProButton.R, StandardInput.R1)
        setButton(NativeInput.ProButton.ZL, StandardInput.L2)
        setButton(NativeInput.ProButton.ZR, StandardInput.R2)
        setButton(NativeInput.ProButton.PLUS, StandardInput.START)
        setButton(NativeInput.ProButton.MINUS, StandardInput.SELECT)
        setButton(NativeInput.ProButton.STICKL, StandardInput.L3)
        setButton(NativeInput.ProButton.STICKR, StandardInput.R3)

        fun setAxis(mappingId: Int, axis: Int, wantPositive: Boolean, invert: Boolean) {
            val actualPositive = if (invert) !wantPositive else wantPositive
            val code = nativeAxisCode(axis, actualPositive) ?: return
            NativeInput.setControllerMapping(descriptor, name, 0, mappingId, code)
        }
        setAxis(NativeInput.ProButton.STICKL_UP, mapping.leftStick.yAxis, wantPositive = false, invert = mapping.leftStick.invertY)
        setAxis(NativeInput.ProButton.STICKL_DOWN, mapping.leftStick.yAxis, wantPositive = true, invert = mapping.leftStick.invertY)
        setAxis(NativeInput.ProButton.STICKL_LEFT, mapping.leftStick.xAxis, wantPositive = false, invert = mapping.leftStick.invertX)
        setAxis(NativeInput.ProButton.STICKL_RIGHT, mapping.leftStick.xAxis, wantPositive = true, invert = mapping.leftStick.invertX)
        setAxis(NativeInput.ProButton.STICKR_UP, mapping.rightStick.yAxis, wantPositive = false, invert = mapping.rightStick.invertY)
        setAxis(NativeInput.ProButton.STICKR_DOWN, mapping.rightStick.yAxis, wantPositive = true, invert = mapping.rightStick.invertY)
        setAxis(NativeInput.ProButton.STICKR_LEFT, mapping.rightStick.xAxis, wantPositive = false, invert = mapping.rightStick.invertX)
        setAxis(NativeInput.ProButton.STICKR_RIGHT, mapping.rightStick.xAxis, wantPositive = true, invert = mapping.rightStick.invertX)

        NativeInput.saveInputs()
    }

    /** Cemu's own semantic axis-direction constants, per axis+sign — confirmed against
     *  InputMapper.kt's private getNativeAxisKey. */
    private fun nativeAxisCode(axis: Int, positive: Boolean): Int? = when (axis) {
        MotionEvent.AXIS_X -> if (positive) NativeInput.Axis.X_POS else NativeInput.Axis.X_NEG
        MotionEvent.AXIS_Y -> if (positive) NativeInput.Axis.Y_POS else NativeInput.Axis.Y_NEG
        MotionEvent.AXIS_Z, MotionEvent.AXIS_RX -> if (positive) NativeInput.Axis.ROTATION_X_POS else NativeInput.Axis.ROTATION_X_NEG
        MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY -> if (positive) NativeInput.Axis.ROTATION_Y_POS else NativeInput.Axis.ROTATION_Y_NEG
        MotionEvent.AXIS_LTRIGGER -> if (positive) NativeInput.Axis.TRIGGER_X_POS else null
        MotionEvent.AXIS_RTRIGGER -> if (positive) NativeInput.Axis.TRIGGER_Y_POS else null
        else -> null
    }

    // ---------------------------------------------------------------------------------------
    // RetroArch (N64/SNES/NES/GBA+GB/PS1)
    // ---------------------------------------------------------------------------------------

    /**
     * Patches just the input_player1_* bind lines into RetroArch's own retroarch_romrunner.cfg —
     * never overwrites the whole file, since RetroArch itself saves other user-changed settings
     * back into that same file (config_save_on_exit). Uses the exact raw-Android-keycode values
     * already confirmed in RetroArch's own builtin default table (input_autodetect_builtin.c's
     * ANDROID_DEFAULT_BINDS), and input_player1_<btn>_btn as the key convention confirmed correct
     * for a launch-time CONFIGFILE (as opposed to an autoconfig profile file) via retroarch.cfg's
     * own commented-out example of that exact key.
     */
    fun applyToRetroArch(configFile: File, mapping: ControllerMapping) {
        val b = mapping.buttons
        val lines = mutableListOf<String>()

        fun buttonLine(cfgKey: String, input: StandardInput) {
            val binding = b[input] as? PhysicalBinding.Key ?: return
            lines += "input_player1_${cfgKey}_btn = \"${binding.keyCode}\""
        }

        buttonLine("up", StandardInput.DPAD_UP)
        buttonLine("down", StandardInput.DPAD_DOWN)
        buttonLine("left", StandardInput.DPAD_LEFT)
        buttonLine("right", StandardInput.DPAD_RIGHT)
        // RetroPad's own naming is SNES-derived (a=east/right, b=south/bottom, x=north/top,
        // y=west/left) — same diamond-position convention used everywhere else in this file.
        buttonLine("a", StandardInput.FACE_RIGHT)
        buttonLine("b", StandardInput.FACE_BOTTOM)
        buttonLine("x", StandardInput.FACE_TOP)
        buttonLine("y", StandardInput.FACE_LEFT)
        buttonLine("l", StandardInput.L1)
        buttonLine("r", StandardInput.R1)
        buttonLine("l2", StandardInput.L2)
        buttonLine("r2", StandardInput.R2)
        buttonLine("l3", StandardInput.L3)
        buttonLine("r3", StandardInput.R3)
        buttonLine("select", StandardInput.SELECT)
        buttonLine("start", StandardInput.START)

        val existing = if (configFile.exists()) configFile.readText() else ""
        var text = existing
        for (line in lines) {
            val key = line.substringBefore(" =").trim()
            text = setCfgLine(text, key, line)
        }
        configFile.writeText(text)
    }

    /** Sets a single `key = value` line, replacing an existing one for [key] if present (matches
     *  PrimeHackEmbeddedLauncher's own setIniValue shape, adapted for a flat .cfg with no sections). */
    private fun setCfgLine(cfgText: String, key: String, newLine: String): String {
        val regex = Regex("(?m)^$key\\s*=.*$")
        return if (regex.containsMatchIn(cfgText)) {
            regex.replace(cfgText, newLine)
        } else {
            cfgText.trimEnd('\n') + "\n$newLine\n"
        }
    }

    // ---------------------------------------------------------------------------------------
    // WatermelonDS (Nintendo DS/DSi)
    // ---------------------------------------------------------------------------------------

    /**
     * Writes controller_config.json directly (kotlinx.serialization-backed — the only core here
     * that isn't SharedPreferences/an .ini/.cfg file, confirmed against
     * SharedPreferencesSettingsRepository.kt's own controllerConfiguration lazy-load, which reads
     * this exact file/shape on next access) using the real InputConfigDto/ControllerConfigurationDto
     * schema and Json{ignoreUnknownKeys=true; explicitNulls=false} config (both read directly from
     * source, not guessed).
     *
     * DS has no analog sticks and no L2/R2/L3/R3 — only D-pad, the 4 face buttons, L/R, and
     * Select/Start are written, same scope-limiting pattern already used for 3DS's missing L3/R3 in
     * [applyToAzahar]. DS's own diamond position convention (confirmed against
     * DefaultControllerConfigurationFactory.kt's own default assignment) matches the Nintendo
     * layout used everywhere else in this file: A=right, B=bottom, X=top, Y=left.
     */
    fun applyToWatermelonDS(context: Context, mapping: ControllerMapping) {
        val b = mapping.buttons

        fun key(input: Input, standardInput: StandardInput): InputConfigDto? {
            val binding = b[standardInput] as? PhysicalBinding.Key ?: return null
            return InputConfigDto(
                input = input,
                assignment = InputConfigDto.AssignmentDto.Key(deviceId = null, keyCode = binding.keyCode),
            )
        }

        val entries = listOfNotNull(
            key(Input.UP, StandardInput.DPAD_UP),
            key(Input.DOWN, StandardInput.DPAD_DOWN),
            key(Input.LEFT, StandardInput.DPAD_LEFT),
            key(Input.RIGHT, StandardInput.DPAD_RIGHT),
            key(Input.A, StandardInput.FACE_RIGHT),
            key(Input.B, StandardInput.FACE_BOTTOM),
            key(Input.X, StandardInput.FACE_TOP),
            key(Input.Y, StandardInput.FACE_LEFT),
            key(Input.L, StandardInput.L1),
            key(Input.R, StandardInput.R1),
            key(Input.SELECT, StandardInput.SELECT),
            key(Input.START, StandardInput.START),
        )

        val dto = ControllerConfigurationDto(inputMapper = entries)
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        File(context.filesDir, "controller_config.json").writeText(json.encodeToString(dto))
    }
}
