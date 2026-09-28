#!/bin/bash
# Copies MenuStyle.kt.template / MenuComponents.kt.template into each Compose-based embedded
# core, substituting the __PACKAGE__ placeholder for that core's own package. Run this after
# editing either .template file, then rebuild RomRunner.
#
# Each destination is the SAME package as that core's existing pause-menu file (RomRunnerPauseMenu.kt
# / EmulationScreen.kt / PauseMenuOverlay.kt), so no new imports are needed at the call sites.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")/.."

sync_target() {
    local package="$1" dest_dir="$2"
    mkdir -p "$dest_dir"
    sed "s/^package __PACKAGE__/package $package/" "$SCRIPT_DIR/MenuStyle.kt.template" > "$dest_dir/MenuStyle.kt"
    sed "s/^package __PACKAGE__/package $package/" "$SCRIPT_DIR/MenuComponents.kt.template" > "$dest_dir/MenuComponents.kt"
    echo "Synced -> $dest_dir"
}

sync_target "com.armsx2.ui.emulation" \
    "$ROOT_DIR/armsx2-src/platforms/android/app/src/main/java/com/armsx2/ui/emulation"

sync_target "info.cemu.cemu.emulation" \
    "$ROOT_DIR/cemu-src/src/android/app/src/main/java/info/cemu/cemu/emulation"

sync_target "me.magnum.melonds.ui.emulator.ui" \
    "$ROOT_DIR/watermelonds-src/app/src/main/java/me/magnum/melonds/ui/emulator/ui"

echo "Done. Rebuild RomRunner to apply."
