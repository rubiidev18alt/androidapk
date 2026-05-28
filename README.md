# IBM PC 5150 Android Emulator

This project turns the original Android APK template into a tiny educational IBM PC Model 5150 emulator shell for Android.

## Emulated profile

- IBM PC Model 5150 style machine
- Intel 8088 CPU target clock: 4.77 MHz
- 16 KB configured base RAM target
- CGA-style 80x25 text display
- Android soft-keyboard input
- Optional GLaBIOS ROM loading from `app/src/main/assets/roms/glabios.bin`
- Built-in IBM 5150 ROM-style fallback when `glabios.bin` is not present

## Important accuracy note

This is not yet a full PC emulator like 86Box, MartyPC, or VirtualXT. The app contains a small Kotlin 8088 instruction interpreter and a CGA text screen scaffold. It is useful as a starting point for boot/BIOS experiments, but real DOS compatibility needs a much larger CPU core, DMA, PIC, PIT, PPI, FDC, disk, cassette, and hardware timing work.

## Built-in fallback BIOS

If `app/src/main/assets/roms/glabios.bin` is missing, the app uses a restrained IBM PC 5150-style fallback instead of a fake custom BIOS brand. It initializes the display, reports 16 KB of memory, exposes basic BIOS interrupt stubs, then enters a minimal monitor because no disk image is attached.

Fallback behavior:

- POST-style 5150-compatible text output
- INT 10h teletype, clear screen, and mode query stubs
- INT 11h equipment-list stub
- INT 12h base-memory-size stub returning 16 KB
- INT 16h blocking keyboard read
- INT 19h reboot back to the fallback screen
- Minimal `>` monitor with `HELP`, `MEM`, `PORTS`, `INT`, `CLS`, and `REBOOT`

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
