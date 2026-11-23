# AmbiBack Manager

**English** · [Deutsch](README.de.md)

Android app for setting up and controlling AmbiBack devices. It finds devices on the network, configures their Wi-Fi and embeds [AmbiBack UI](https://github.com/ambiback/ambiback-ui) as its full-control view.

It is part of the [AmbiBack project](https://github.com/ambiback) and works with the [AmbiBack firmware](https://github.com/ambiback/ambiback-firmware).

## Status

- Version 1.2, Android 8.0 (API 26) or newer.
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

## License

Copyright (C) 2019-2026 Andre Lorbach

This program is free software under the [GNU General Public License version 3](LICENSE). It comes with no warranty. Included libraries keep their own licenses.
