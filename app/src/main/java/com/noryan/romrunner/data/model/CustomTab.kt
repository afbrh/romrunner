package com.noryan.romrunner.data.model

/**
 * A menu the user made themselves with the "+" in the tab bar: a name and the games and apps they chose for it.
 * Games are remembered by their file's address (it survives a rescan, unlike a database id) and apps by package name;
 * an entry whose game or app is gone is simply left out when the menu is shown.
 */
data class CustomTab(
    val id: String,
    val name: String,
    val gameUris: List<String>,
    val appPackages: List<String>
)
