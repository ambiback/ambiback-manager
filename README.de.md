# AmbiBack Manager

[English](README.md) · **Deutsch**

Android-App zum Einrichten und Steuern von AmbiBack-Geräten. Sie findet Geräte im Netzwerk, richtet ihr WLAN ein und bindet die [AmbiBack UI](https://github.com/ambiback/ambiback-ui) als Full-Control-Ansicht ein.

Sie gehört zum [AmbiBack-Projekt](https://github.com/ambiback) und arbeitet mit der [AmbiBack-Firmware](https://github.com/ambiback/ambiback-firmware).

Wie die App bedient wird, steht mit Bildern im [Benutzerhandbuch](docs/BENUTZERHANDBUCH.md).

## Status

- Version 1.2, Android 8.0 (API 26) oder neuer.
- Der Debug-Build und die 15 Unit-Tests liefen bei der Migration erfolgreich.
- **Die App wurde nicht auf einem echten Gerät und nicht mit echter AmbiBack-Hardware getestet.**
- AmbiBack-Geräte werden seit rund fünf Jahren nicht mehr verkauft. Dieses Repository dient dem Eigenbau und der Weiterentwicklung, ohne Produktsupport. Mehr auf [ambiback.de](https://www.ambiback.de/).

## Funktionen

- Geräte per UDP im lokalen Netzwerk finden
- WLAN-Einrichtung über den Access Point des Geräts
- Gerätezugriff und Firmware-Flashing über USB
- Full-Control-Ansicht mit der eingebetteten Weboberfläche
- Oberfläche auf Deutsch und Englisch

## Sicherheit

Geräte mit Access Point öffnen ihr Einrichtungsfenster nur, wenn noch kein WLAN eingerichtet ist, oder nach einer Anforderung direkt am Gerät: die Taste 4 bis 7 Sekunden halten oder den Strom dreimal im Abstand weniger Sekunden einschalten. Der AmbiBack Manager koppelt sich dann mit dem Gerät und speichert einen Geräteschlüssel. Nach der Kopplung brauchen WLAN-Einrichtung, Access-Point- und Debug-Einstellungen, Neustart, WPS und Firmware-Updates diesen Schlüssel.

Verbleibende Grenzen: Die Ersteinrichtung ist nicht dagegen geschützt, dass jemand in Funkreichweite sich zuerst koppelt. Das erzeugte Access-Point-Passwort wird weiter aus der MAC-Adresse abgeleitet, ein eigener Startwert ist kein vollwertiger Schutz, und die Übertragung ist unverschlüsselt. LED- und Modussteuerung bleiben im lokalen Netz offen, und Gerätetypen ohne Access Point haben keine Kopplung. Betreibe die Geräte nur in einem vertrauenswürdigen Netz.

## Hardware

Die Bauteile für den Selbstbau stehen auf der [AmbiBack-Teileseite](https://www.ambiback.de/selbstbau/teile/).

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

Copyright (C) 2019-2026 Andre Lorbach

Dieses Programm ist freie Software und steht unter der [GNU General Public License Version 3](LICENSE). Es wird ohne jede Gewährleistung bereitgestellt. Eingebundene Bibliotheken behalten ihre eigenen Lizenzen.
