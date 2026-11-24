# AmbiBack Manager

**English** · [Deutsch](README.de.md)

Android app for setting up and controlling AmbiBack devices. It finds devices on the network, configures their Wi-Fi and embeds [AmbiBack UI](https://github.com/ambiback/ambiback-ui) as its full-control view.

It is part of the [AmbiBack project](https://github.com/ambiback) and works with the [AmbiBack firmware](https://github.com/ambiback/ambiback-firmware).

A user guide with screenshots is available in German: [Benutzerhandbuch](docs/BENUTZERHANDBUCH.md).

## Status

- Version 1.4, Android 8.0 (API 26) or newer.
- CI builds the app and runs the unit tests and lint on every change.
- **The app has not been tested on a real device or with real AmbiBack hardware.**
- AmbiBack devices have not been on sale for around five years. This repository is for DIY use and further development, with no product support. More at [ambiback.de](https://www.ambiback.de/).

## Features

- Find devices over UDP on the local network
- Wi-Fi setup through the device's access point
- Device access and firmware flashing over USB
- Full-control view with the embedded web interface
- German and English interface

## Security

Devices with an access point open their setup window only when no Wi-Fi is configured, or after a physical request: hold the button for 4 to 7 seconds, or switch the power on three times within a few seconds each. AmbiBack Manager then pairs with the device and stores a device key. After pairing, Wi-Fi setup, access point and debug settings, reboot, WPS and firmware updates require this key.

Remaining limits: the first setup is not protected against someone in radio range who pairs first. The generated access point password is still derived from the MAC address, a custom seed is not full protection, and traffic is unencrypted. LED and mode control stay open on the local network, and device types without an access point have no pairing. Run devices only on a trusted network.

## Hardware

The parts for a DIY build are on the [AmbiBack parts page](https://www.ambiback.de/en/diy/parts/).

## Building

Requires JDK 17 or newer and the Android SDK with API level 34.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The full-control interface is included prebuilt in `app/src/main/assets/fullcontrol/`. To regenerate it from the current `ambiback-ui` (requires Git and Node.js):

```sh
./gradlew assembleDebug -PsyncAmbibackUi
```

If your firmware uses a custom `AMBIBACK_AP_PASSWORD_SEED`, build the app with the same value:

```sh
./gradlew assembleDebug -PambibackApPasswordSeed=0x12345678
```

## Release

Releases are built by GitHub Actions. Raise `versionCode` and `versionName` in `app/build.gradle`, merge to `main`, then push a matching tag:

```sh
git tag v1.3
git push origin v1.3
```

The workflow checks that the tag is on `main`, matches `versionName` and has a higher `versionCode` than the previous tag. It then runs the tests and lint, builds a signed APK and AAB, and publishes them with a QR code as a GitHub release.

Signing uses the same scheme as [legacy-solar-monitor](https://github.com/alorbach/legacy-solar-monitor). Set these repository secrets once:

| Secret | Content |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The keystore file, Base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

To sign a local release build, put `release.store.file`, `release.store.password`, `release.key.alias` and `release.key.password` in `local.properties`. Without them `assembleRelease` produces an unsigned APK.

## License

Copyright (C) 2019-2026 Andre Lorbach

This program is free software under the [GNU General Public License version 3](LICENSE). It comes with no warranty. Included libraries keep their own licenses.
