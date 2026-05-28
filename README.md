# IBM PC 5150 Android Emulator

This project turns the original Android APK template into a tiny educational IBM PC Model 5150 emulator shell for Android.

## Emulated profile

- IBM PC Model 5150 style machine
- Intel 8088 CPU target clock: 4.772727 MHz
- 16 KB configured base RAM target
- CGA-style 80x25 text display using a built-in 8x8 bitmap font renderer
- White-on-black CGA text appearance
- Android soft-keyboard input
- Optional GLaBIOS ROM loading from `app/src/main/assets/roms/glabios.bin`
- Optional floppy image loading from `app/src/main/assets/floppy/disk0.img`
- Built-in IBM 5150 ROM-style fallback when `glabios.bin` is not present

## Important accuracy note

This is not yet a full PC emulator like 86Box, MartyPC, or VirtualXT. The app contains a small Kotlin 8088 instruction interpreter and a CGA text screen scaffold. It is useful as a starting point for boot/BIOS experiments, but real DOS compatibility still needs a much larger CPU core, DMA, PIC, PIT, PPI, FDC command behavior, disk-change logic, cassette, and hardware timing work.

## Floppy support

The app can load a raw floppy image from:

```text
app/src/main/assets/floppy/disk0.img
```

The fallback BIOS has simple `INT 13h` support for reset and CHS sector reads. It assumes a 160 KB single-sided/double-sided-era layout style of 512-byte sectors and 8 sectors per track. This is enough for basic boot-sector experiments, not full IBM 5150 disk-controller accuracy.

## Built-in fallback BIOS

If `app/src/main/assets/roms/glabios.bin` is missing, the app uses a restrained IBM PC 5150-style fallback instead of a fake custom BIOS brand. It initializes the display, reports 16 KB of memory, exposes basic BIOS interrupt stubs, then enters a minimal monitor.

Fallback behavior:

- POST-style 5150-compatible text output
- INT 10h teletype, clear screen, and mode query stubs
- INT 11h equipment-list stub
- INT 12h base-memory-size stub returning 16 KB
- INT 13h floppy reset and sector-read stubs
- INT 16h blocking keyboard read
- INT 19h loads sector 0 to `0000:7C00` when a floppy image is present
- Minimal `>` monitor with `HELP`, `MEM`, `PORTS`, `INT`, `DISK`, `BOOT`, `CLS`, and `REBOOT`

## CGA font

The renderer now uses a built-in 8x8 bitmap font table instead of Android's proportional text rendering. It is closer to CGA text-mode behavior and avoids shipping third-party font binaries. For higher fidelity, replace or expand `CgaBitmapFont.kt` with IBM CGA ROM glyph data from a legally redistributable source and keep that source's license in this README.

## GLaBIOS

GLaBIOS is not bundled as a binary in this repository. To use it:

1. Download or build a GLaBIOS ROM from the upstream project.
2. Place it at:

```text
app/src/main/assets/roms/glabios.bin
```

3. Build the APK.

Upstream credit:

- GLaBIOS by 640KB and contributors: `640-KB/GLaBIOS`
- License: GNU GPLv3
- Purpose: open-source BIOS for vintage PC, XT, 8088 clone, turbo, and homebrew PCs

The app code is written from scratch for this repository. GLaBIOS support is by ROM loading/credit, not copied source code.

## Build APK

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If your machine does not have Gradle installed, either install Gradle or add the standard Gradle Wrapper files.
