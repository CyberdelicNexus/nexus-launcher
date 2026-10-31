# Cyberdelic Nexus Launcher

A Meta Quest launcher that shows one showcase menu as a grid of experiences, with
each experience's facilitator guide beside it. It works with controllers and hand
tracking, in passthrough or VR.

This module is part of a fork of [Rusty Kiosk](https://github.com/MesmerPrism/Rusty-Kiosk)
by MesmerPrism. Its Spatial SDK host and front-door discovery are adapted from Rusty
Kiosk and Rusty Launcher Lite. It is licensed under AGPL-3.0-or-later, like the rest
of this repository. See `LICENSE` and `THIRD_PARTY_NOTICES.md`. It is an independent
project, not affiliated with or endorsed by Meta.

## Build

Requires Unity's Android module (for its JDK and SDK) and the signing file described
below. No Android Studio is needed.

```powershell
.\nexus-launcher\build.ps1 -VersionCode 1 -VersionName 0.1.0
```

This runs the unit tests, builds the signed release APK, and copies it to
`quest_fleet/android/nexus-launcher/dist/` for Quest Fleet to install. The same APK
is the one to upload to a Meta release channel. Raise `-VersionCode` for each upload.

The signing file defaults to `~/.cyberdelic/nexus-launcher/signing.json` and holds
`path`, `password` and `alias`. Keep it and the keystore out of this repository.
Losing the key means headsets and Meta will refuse updates.

## The menu

The app ships with no menu. Quest Fleet generates `nexus.catalog.v1` from its
experience guides and delivers it through `CatalogProvider`, which only the ADB shell
can call:

```sh
adb shell "content write --user 0 --uri content://nexus.cyberdelic.launcher.catalog/catalog < /data/local/tmp/menu.json"
adb shell content call --user 0 --uri content://nexus.cyberdelic.launcher.catalog --method status
```

A delivery that fails validation is rejected whole and the last good menu stays.
