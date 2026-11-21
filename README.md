# SBL SUPER BOOST V2

Native Android-App (Kotlin, Jetpack Compose, minSdk 24, targetSdk 34).

## Was die App wirklich tut

| Funktion | Wirkung | Umsetzung |
|---|---|---|
| Regler 0–100 % | **Alle Apps** (YouTube, Spotify, TikTok, Spiele, Bluetooth) | `AudioManager.setStreamVolume(STREAM_MUSIC)`; Anzeige = real gelesener Wert |
| Regler 100–200 % | **Nur Audio des In-App-Players** | `LoudnessEnhancer` (+0 … +6,02 dB, = 1,0× … 2,0× Amplitude) |
| Equalizer (Bass/Mitten/Höhen, Presets, Einzelbänder) | **Nur In-App-Player** | `android.media.audiofx.Equalizer` auf der eigenen Audio-Session |
| Clipping-Schutz | Nur In-App-Player | `DynamicsProcessing`-Limiter (ab Android 9); darunter nur die interne Begrenzung des LoudnessEnhancers |
| Hardware-Tasten | Sync bis 100 % per `ContentObserver`; bei App im Vordergrund und ≥100 % steuern die Tasten die Verstärkung (±5 %) | |
| Bluetooth | Zeigt Name/Status verbundener und gekoppelter Audiogeräte (A2DP, LE Audio, Hörgeräte), Ausgabewahl für den In-App-Player (`MediaPlayer.setPreferredDevice`, ab Android 9) | `AudioDeviceCallback`, `BluetoothManager` |

## Was Android einer normalen App NICHT erlaubt (ehrliche Dokumentation)

- **Keine systemweite digitale Verstärkung.** Effekte auf der globalen Mix-Session (Session 0) sind seit Android 4.x/10+ für Apps wirkungslos bzw. deprecated. `Equalizer`, `LoudnessEnhancer` und `DynamicsProcessing` wirken nur auf die Audio-Session, an die sie gebunden sind – hier die des eigenen Players.
- **`AudioPlaybackCapture` (ab Android 10)** kann Audio anderer Apps nur mitschneiden (Berechtigung per MediaProjection + `RECORD_AUDIO`, und nur wenn die Quell-App das erlaubt – Spotify/TikTok u. a. sperren es). Das Original läuft dabei unverändert weiter; man könnte eine verstärkte Kopie nur *zusätzlich* ausgeben. Das ist keine echte Verstärkung und wurde bewusst **nicht** implementiert.
- Echte systemweite Verstärkung braucht einen vom Gerätehersteller/ROM eingebundenen Audioeffekt, Root oder Systemrechte – das kann eine normale App nicht bereitstellen.
- Über 100 % wirkt die Verstärkung also nur für Dateien, die in dieser App abgespielt werden. Für andere Apps bleibt es bei der Systemlautstärke (max. 100 %).
- Kein Foreground-Service: Der In-App-Player stoppt, wenn Android den App-Prozess beendet.
- Bluetooth-Geräteauswahl gilt nur für den In-App-Player; für andere Apps wechselt man das Gerät im Android-System (Button „System-Ausgabe“).
- Nicht jedes Gerät/jede ROM stellt alle Effekte bereit; die App zeigt im „Technischen Status“, was tatsächlich verfügbar ist.

## Bauen

**GitHub Actions:** Projekt in ein Repository hochladen → Workflow `Build Debug APK` läuft bei jedem Push (oder manuell unter *Actions*). Die APK liegt danach als Artifact `SBL-SUPER-BOOST-V2-debug-apk`.
Der Workflow nutzt JDK 17 und installiert Gradle 8.7 selbst – ein `gradle-wrapper.jar` ist daher nicht nötig.

**Lokal:** In Android Studio öffnen (Hedgehog oder neuer) oder mit installiertem Gradle 8.7: `gradle assembleDebug`.
Optional Wrapper erzeugen: `gradle wrapper --gradle-version 8.7`.

## Prüfstatus

Statisch geprüft: XML-Validität, Klammerbalance, interne Imports, Ressourcen-Referenzen im Manifest, YAML-Syntax des Workflows.
**Nicht** durchgeführt: echter Gradle-/Kotlin-Build und Test auf einem Gerät (in der Erstellungsumgebung nicht möglich).
Der erste GitHub-Actions-Lauf ist daher der erste echte Compile-Test. Bei Fehlern die erste Meldung im Schritt „Debug-APK bauen“ prüfen.

## Projektstruktur

```
.github/workflows/build-debug-apk.yml
build.gradle.kts · settings.gradle.kts · gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/sbl/superboost/   MainActivity, MainViewModel
  audio/  VolumeController, AudioEngine, BluetoothAudio, EqPresets
  ui/     MainScreen, Theme
app/src/main/res/                      values (strings, colors, themes), drawable/ic_launcher.xml
```

Berechtigungen: `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH` (nur bis Android 11), `BLUETOOTH_CONNECT` (Laufzeit-Abfrage ab Android 12, für Gerätenamen/gekoppelte Geräte).
