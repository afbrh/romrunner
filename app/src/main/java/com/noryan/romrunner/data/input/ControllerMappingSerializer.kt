package com.noryan.romrunner.data.input

import org.json.JSONObject

/**
 * Packs/unpacks a [ControllerMapping] as one JSON blob (matching the existing "one pref, one JSON
 * blob" pattern already used elsewhere in this codebase, e.g. PS2EmbeddedLauncher's config.global —
 * no new serialization dependency needed). Any slot missing or unparseable in stored JSON falls
 * back to [ControllerMapping.AYN_THOR_DEFAULT]'s value for that slot, so a future build adding a
 * new slot (or a corrupted pref) degrades gracefully instead of crashing.
 */
object ControllerMappingSerializer {

    private const val KEY_BUTTONS = "buttons"
    private const val KEY_LEFT_STICK = "leftStick"
    private const val KEY_RIGHT_STICK = "rightStick"

    private const val KEY_TYPE = "type"
    private const val TYPE_KEY = "key"
    private const val TYPE_AXIS = "axis"
    private const val KEY_KEYCODE = "keyCode"
    private const val KEY_AXIS = "axis"
    private const val KEY_POSITIVE = "positive"

    private const val KEY_X_AXIS = "xAxis"
    private const val KEY_Y_AXIS = "yAxis"
    private const val KEY_INVERT_X = "invertX"
    private const val KEY_INVERT_Y = "invertY"

    fun toJson(mapping: ControllerMapping): String {
        val root = JSONObject()
        val buttons = JSONObject()
        mapping.buttons.forEach { (input, binding) -> buttons.put(input.name, bindingToJson(binding)) }
        root.put(KEY_BUTTONS, buttons)
        root.put(KEY_LEFT_STICK, stickToJson(mapping.leftStick))
        root.put(KEY_RIGHT_STICK, stickToJson(mapping.rightStick))
        return root.toString()
    }

    fun fromJson(json: String): ControllerMapping {
        val default = ControllerMapping.AYN_THOR_DEFAULT
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return default

        val buttonsJson = root.optJSONObject(KEY_BUTTONS)
        val buttons = StandardInput.entries
            .filter { it != StandardInput.LEFT_STICK && it != StandardInput.RIGHT_STICK }
            .associateWith { input ->
                val bindingJson = buttonsJson?.optJSONObject(input.name)
                bindingJson?.let { bindingFromJson(it) } ?: default.buttons.getValue(input)
            }

        val leftStick = root.optJSONObject(KEY_LEFT_STICK)?.let { stickFromJson(it) } ?: default.leftStick
        val rightStick = root.optJSONObject(KEY_RIGHT_STICK)?.let { stickFromJson(it) } ?: default.rightStick

        return ControllerMapping(buttons, leftStick, rightStick)
    }

    private fun bindingToJson(binding: PhysicalBinding): JSONObject = JSONObject().apply {
        when (binding) {
            is PhysicalBinding.Key -> {
                put(KEY_TYPE, TYPE_KEY)
                put(KEY_KEYCODE, binding.keyCode)
            }
            is PhysicalBinding.Axis -> {
                put(KEY_TYPE, TYPE_AXIS)
                put(KEY_AXIS, binding.axis)
                put(KEY_POSITIVE, binding.positiveDirection)
            }
        }
    }

    private fun bindingFromJson(json: JSONObject): PhysicalBinding? = runCatching {
        when (json.getString(KEY_TYPE)) {
            TYPE_KEY -> PhysicalBinding.Key(json.getInt(KEY_KEYCODE))
            TYPE_AXIS -> PhysicalBinding.Axis(json.getInt(KEY_AXIS), json.getBoolean(KEY_POSITIVE))
            else -> null
        }
    }.getOrNull()

    private fun stickToJson(stick: StickBinding): JSONObject = JSONObject().apply {
        put(KEY_X_AXIS, stick.xAxis)
        put(KEY_Y_AXIS, stick.yAxis)
        put(KEY_INVERT_X, stick.invertX)
        put(KEY_INVERT_Y, stick.invertY)
    }

    private fun stickFromJson(json: JSONObject): StickBinding? = runCatching {
        StickBinding(
            xAxis = json.getInt(KEY_X_AXIS),
            yAxis = json.getInt(KEY_Y_AXIS),
            invertX = json.optBoolean(KEY_INVERT_X, false),
            invertY = json.optBoolean(KEY_INVERT_Y, false),
        )
    }.getOrNull()
}
