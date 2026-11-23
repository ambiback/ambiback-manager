# AmbiBack Manager

**English** · [Deutsch](README.de.md)

Android app for setting up and controlling AmbiBack devices. It finds devices on the network, configures their Wi-Fi and embeds [AmbiBack UI](https://github.com/ambiback/ambiback-ui) as its full-control view.

It is part of the [AmbiBack project](https://github.com/ambiback) and works with the [AmbiBack firmware](https://github.com/ambiback/ambiback-firmware).

## Status

- Version 1.1, Android 8.0 (API 26) or newer.
- The debug build and the 15 unit tests passed during the migration.
- **The app has not been tested on a real device or with real AmbiBack hardware.**
- AmbiBack devices have not been on sale for around five years. This repository is for DIY use and further development, with no product support. More at [ambiback.de](https://www.ambiback.de/).

## Features

- Find devices over UDP on the local network
- Wi-Fi setup through the device's access point
- Device access and firmware flashing over USB
- Full-control view with the embedded web interface
- German and English interface

## Security

Without a custom password, the firmware derives the device access point password from the MAC address, which is also part of the Wi-Fi name. The device API has no login. Set your own access point password or turn the access point off after setup, and run devices only on a trusted network.

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

## License

Copyright (C) 2019-2026 Andre Lorbach

This program is free software under the [GNU General Public License version 3](LICENSE). It comes with no warranty. Included libraries keep their own licenses.
