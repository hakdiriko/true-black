# True Black — Android

Turns every dark grey on an OLED phone into pure `#000000` — pixels off — while white text, photos and colour keep their full range. One tap, or a Quick Settings tile.

It is the Android counterpart of the Windows *True Black OLED Engine*. Same colour model, same presets; the compositor just has a different name.

## How it works

Android's display compositor, **SurfaceFlinger**, accepts a single 4×4 client colour matrix (the same hook Night Light and Extra dim use). True Black sets that matrix once:

```
C' = max(0, (sat(C) − T) / (1 − T))
```

* Greys at or below the floor `T` land exactly on 0.0 → the OLED pixel is switched off.
* Everything above is re-stretched so white stays white; an optional saturation gain keeps colour vivid.
* On most SoCs the display hardware (DSPP / DPU) applies the matrix, so there is no GPU or CPU cost. The app does not run in the background; the matrix simply stays set until reboot.

The matrix endpoint needs shell or root identity, so the app talks through:

| Access | How |
| --- | --- |
| **Shizuku** (recommended, no PC) | One binder transaction forwarded with shell identity. Start Shizuku via *Wireless debugging* once per boot, or let it autostart with root. Sui is supported automatically. |
| **Root** | `su -c service call SurfaceFlinger 1015 …` |
| **ADB** (manual) | The app shows the exact `adb shell` command for the current calibration. |

## Features

* Hero toggle + Quick Settings tile (the Scroll Lock of Android)
* Presets: Standard 12 %, Deep 16 %, Vivid 14 % / 1.25×, Subtle 8 %
* Live calibration with a true grey ramp — what collapses to black in the ramp is what the compositor does to the whole screen
* Restore after reboot
* Optional **Re-apply on unlock**: Night Light / Extra dim overwrite the compositor matrix; this re-pushes it on every unlock (one call, no polling)
* Optional system dark-theme sync (`cmd uimode night`)
* English and Turkish; pure black UI that stays readable while the floor is active

## Build

GitHub Actions builds a signed `TrueBlack.apk` on every push to `main` (see *Releases* / workflow artifacts).

Locally: Android Studio Ladybug or newer → *Build › Build APK*, or

```
./gradlew assembleRelease
```

The release keystore in `keystore/` is intentionally committed — this is a personal side-loaded app, and a stable key makes updates installable over the previous build.

## Notes

* Devices whose HWC cannot apply a colour transform fall back to GPU composition while the floor is active (slightly higher power on those; most 2019+ phones are fine).
* The threshold is nominally in display-encoded (sRGB) values. If your vendor applies the matrix in linear light, simply calibrate with the ramp — it always tells the truth.
* Android 10+. No network permission.
