package com.noryan.romrunner.data.launch

/**
 * The libretro cores RomRunner offers for each system RetroArch plays, for the "Core" choice on the Systems tab. The first
 * entry of each list is the one RomRunner uses by default (see RetroArchLauncher.defaultCore); a system with only one
 * entry has nothing to choose. All of them are on libretro's Android build server, and a core is downloaded the first time it's used.
 */
object RetroArchCores {
    data class Option(val id: String, val label: String)

    private val OPTIONS: Map<String, List<Option>> = mapOf(
        "GameBoy (Color + Advance)" to listOf(
            Option("mgba", "mGBA"), Option("gambatte", "Gambatte (GB and GBC only)"),
            Option("vba_next", "VBA Next"), Option("gpsp", "gpSP")
        ),
        "NES" to listOf(Option("nestopia", "Nestopia"), Option("fceumm", "FCEUmm"), Option("quicknes", "QuickNES"), Option("mesen", "Mesen")),
        "SNES" to listOf(Option("snes9x", "Snes9x"), Option("snes9x2010", "Snes9x 2010"), Option("mesen-s", "Mesen-S"), Option("bsnes", "bsnes")),
        "Nintendo 64" to listOf(Option("mupen64plus_next_gles3", "Mupen64Plus-Next"), Option("parallel_n64", "ParaLLEl N64")),
        "PlayStation" to listOf(Option("swanstation", "SwanStation"), Option("pcsx_rearmed", "PCSX ReARMed"), Option("mednafen_psx", "Beetle PSX")),
        "Sega Genesis" to listOf(Option("genesis_plus_gx", "Genesis Plus GX"), Option("picodrive", "PicoDrive")),
        "Sega Master System" to listOf(Option("genesis_plus_gx", "Genesis Plus GX"), Option("picodrive", "PicoDrive"), Option("gearsystem", "Gearsystem")),
        "Sega Game Gear" to listOf(Option("genesis_plus_gx", "Genesis Plus GX"), Option("gearsystem", "Gearsystem")),
        "PC Engine / TurboGrafx-16" to listOf(Option("mednafen_pce_fast", "Beetle PCE Fast"), Option("mednafen_pce", "Beetle PCE")),
        "Atari 2600" to listOf(Option("stella", "Stella"), Option("stella2014", "Stella 2014")),
        "Atari Lynx" to listOf(Option("handy", "Handy"), Option("mednafen_lynx", "Beetle Lynx")),
        "Neo Geo Pocket" to listOf(Option("mednafen_ngp", "Beetle NeoPop"), Option("race", "RACE"))
    )

    /** Names for the cores that have just the one choice, so the Core line can still show what's in use. */
    private val SINGLE = mapOf(
        "picodrive" to "PicoDrive", "prosystem" to "ProSystem", "mednafen_wswan" to "Beetle Cygne", "mednafen_vb" to "Beetle VB"
    )

    /** The cores that can play [platformName]; just the default (or empty) when there's no choice. */
    fun optionsFor(platformName: String): List<Option> = OPTIONS[platformName].orEmpty()

    fun label(id: String): String =
        OPTIONS.values.flatten().firstOrNull { it.id == id }?.label ?: SINGLE[id] ?: id
}
