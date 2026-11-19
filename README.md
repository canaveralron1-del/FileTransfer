# File Transfer

Peer-to-peer Android file transfer over Wi-Fi / Wi-Fi Direct — no internet, no cloud, no cables. Files are sent directly between devices on the same local network over an encrypted TCP connection, with a modern Material 3 interface built in Jetpack Compose.

## Features

- **Direct device-to-device transfer** — no server, no internet connection, no cloud upload
- **Encrypted transfers** — TLS 1.2/1.3 with self-signed certificates and certificate fingerprint verification
- **Automatic device discovery** — UDP broadcast/multicast discovery on the local network, plus Wi-Fi Direct for ad-hoc connections
- **Manual IP entry** — connect directly when discovery is unavailable or blocked by AP isolation
- **QR code pairing & PIN verification** — scan a QR code to connect, optionally require a PIN on the receiving device
- **Multi-file sending** — select any file type, share from other apps via the system share sheet, or optionally bundle files into a ZIP
- **Resumable transfers** — interrupted downloads can continue from the last received byte
- **Integrity checks** — checksum verification after transfer
- **Background receiving** — foreground service with notifications keeps receiving while the app is in the background
- **Transfer progress** — per-file progress, speed, and ETA
- **Customizable themes** — several built-in color palettes (Emerald, Violet, Teal, Rose, Amber) plus a custom option
- **Any file type** — photos, videos, audio, documents, archives, APKs, and more (up to 10 GB per file)

## Requirements

- **Android 7.0 (API 24) or newer**
- Both devices on the same Wi-Fi network, or Wi-Fi Direct available on both devices
- Storage permission to save received files (on older Android versions)

## Build

### Using Android Studio

1. Clone the repository.
2. Open the project folder in Android Studio (Ladybug or newer recommended).
3. Let Gradle sync, then run the `app` configuration on a device or emulator.

### Using the command line

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

To install directly on a connected device:

```bash
./gradlew installDebug
```

### Tech stack

| Component | Version |
| --- | --- |
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.12.3 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| Jetpack Compose (BOM) | 2024.12.01 |
| Material 3 | via Compose BOM |
| Coroutines | 1.9.0 |
| BouncyCastle | 1.80 |
| ZXing (QR codes) | 3.5.3 |

## Usage

### Sending files

1. Open the app on the sending device and stay on the **Send** tab.
2. Make sure the receiving device is on the **Receive** tab.
3. Wait for the receiver to appear in **Available Devices** (tap the radar icon to rescan), or enter its IP manually in Settings.
4. Tap **Select Files** and choose one or more files. You can also share files to the app from another app's share menu.
5. Select the target device and tap **Send Files**. Enable **Zip to save space** if you want the files bundled into a single archive.
6. Watch per-file progress; a checksum is verified when the transfer completes.

### Receiving files

1. Open the app on the receiving device and switch to the **Receive** tab.
2. Grant the requested storage / notification permissions. The device is now discoverable and shows its IP address and PIN (if enabled).
3. If PIN verification is on, enter the PIN displayed on the receiver when prompted on the sender.
4. Received files are saved to `Downloads/FileTransfer` with a timestamp appended to the filename to avoid overwrites.
5. Toggle **Keep receiving in background** in Settings to continue receiving while using other apps.

### Settings

- **Show manual IP input** — connect by IP address instead of discovery
- **Keep receiving in background** — receive via a foreground service
- **Require PIN to connect** — add a PIN verification step
- **Enable QR code sharing** — show/scan QR codes for quick pairing
- **WiFi Direct** — scan for and connect to devices directly
- **Color Theme** — choose from the built-in palettes or a custom one

## How it works

- **Discovery:** A UDP broadcast/multicast probe on port `5002` searches for receivers; responders reply with their IP address and device name.
- **Transfer:** A TCP connection on port `5001` carries a framed stream: file header (name + size), optional resume offset, PIN handshake (if enabled), file data in 256 KB chunks, and a checksum.
- **Security:** Each session generates a self-signed certificate using BouncyCastle. Peers verify the certificate fingerprint, and TLS 1.3/1.2 encrypts all payload data. An optional shared PIN adds a second layer of verification.
- **Resume:** If a transfer is interrupted, the receiver reports how many bytes it has; the sender continues from that offset.
- **Zip mode:** Selected files are compressed into a single ZIP stream before transmission to reduce transfer size.

## Project structure

```
app/src/main/java/com/example/filetransfer/
├── MainActivity.kt                 # Activity, permissions, share intents, file pickers
├── data/
│   ├── local/FileStorage.kt        # Saving received files via ContentResolver
│   ├── model/Models.kt             # UI/data models
│   └── network/
│       ├── DeviceDiscovery.kt      # UDP device discovery
│       ├── FileTransfer.kt         # TCP/TLS protocol, framing, checksums, resume
│       ├── QrUtils.kt              # QR generation and scanning helpers
│       └── WifiDirectManager.kt    # Wi-Fi Direct discovery and connection
├── presentation/MainViewModel.kt   # State, progress, send/receive orchestration
├── service/ReceiveService.kt       # Foreground receive service and notifications
└── ui/
    ├── AppRoot.kt                  # Navigation, tabs, dialogs, theming
    ├── components/                 # Reusable cards, radar, transfer items, settings
    ├── screens/                    # SendScreen and ReceiveScreen
    └── theme/                      # Colors, typography, Material 3 theme, palettes
```

## Known limitations

- Devices must share a local network or support Wi-Fi Direct; AP/client isolation on some guest or public Wi-Fi networks can block device-to-device traffic (use Wi-Fi Direct in that case).
- Maximum file size is 10 GB; very large files take longer and require a stable connection.
- QR pairing requires a camera on the scanning device.
- A desktop (Windows) companion built with Compose Multiplatform is planned; see [`docs/DESKTOP_MIGRATION_PLAN.md`](docs/DESKTOP_MIGRATION_PLAN.md).

## Screenshots

<!-- Add screenshots here, e.g.:
![Send tab](docs/images/send.png)
![Receive tab](docs/images/receive.png)
-->

## License

No license has been specified yet — add a `LICENSE` file before publishing.
