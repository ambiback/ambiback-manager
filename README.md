# AmbiBack Manager

Android-App zum Einrichten und Steuern von AmbiBack-Geräten. Sie findet Geräte im Netzwerk, richtet ihr WLAN ein und bindet die [AmbiBack UI](https://github.com/ambiback/ambiback-ui) als Full-Control-Ansicht ein.

Die AmbiBack-Geräte werden seit rund fünf Jahren nicht mehr verkauft. Dieses Repository stellt den Quellcode für den Eigenbau und die Weiterentwicklung bereit. Mehr zum Projekt steht auf [ambiback.de](https://www.ambiback.de/). Die passende Firmware liegt in [ambiback-firmware](https://github.com/ambiback/ambiback-firmware).

## Funktionen

- Geräte per UDP im lokalen Netzwerk finden
- WLAN-Einrichtung über den Access Point des Geräts
- Gerätezugriff und Firmware-Flashing über USB
- Full-Control-Ansicht mit der eingebetteten Web-Oberfläche
- Oberfläche auf Deutsch und Englisch

## Sicherheitshinweis

Ohne eigenes Passwort leitet die Firmware das Passwort des Geräte-Access-Points aus der MAC-Adresse ab, die auch im WLAN-Namen steht. Die Geräte-API hat keine Anmeldung. Setze deshalb ein eigenes AP-Passwort oder schalte den Access Point nach der Einrichtung ab, und betreibe die Geräte nur in einem vertrauenswürdigen Netz.

## Bauen

Voraussetzungen sind JDK 17 oder neuer und das Android SDK mit API-Level 34.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Die Full-Control-Oberfläche liegt fertig gebaut in `app/src/main/assets/fullcontrol/`. So wird sie aus dem aktuellen `ambiback-ui` neu erzeugt (dafür werden Git und Node.js benötigt):

```sh
./gradlew assembleDebug -PsyncAmbibackUi
```

Nutzt deine Firmware einen eigenen `AMBIBACK_AP_PASSWORD_SEED`, muss die App mit demselben Wert gebaut werden:

```sh
./gradlew assembleDebug -PambibackApPasswordSeed=0x12345678
```

## Lizenz

Copyright (C) 2001-2026 André Lorbach

Dieses Programm ist freie Software und steht unter der [GNU General Public License Version 3](LICENSE). Es wird ohne jede Gewährleistung bereitgestellt. Eingebundene Bibliotheken behalten ihre eigenen Lizenzen.
