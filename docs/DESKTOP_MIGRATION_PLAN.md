# Compose Multiplatform Desktop (Windows) Migration Plan

**Status: PLANNED — resume in a new session.**
User chose **Option A**, and specifically the **"New Desktop-only project (Recommended)"** scope:
create a separate lightweight Compose Desktop project that reuses the portable files, provide a
simple desktop file picker + manual-IP + auto network discovery. Keeps the Android app untouched.

---

## Goal
A native Windows `.exe` built with JetBrains Compose Multiplatform for Desktop (JVM) that lets a
Windows PC transfer files with the Android app over the existing TCP/TLS protocol.

## Key finding from code audit
The networking core and ALL UI are already JVM-portable. Only a small set of files are
Android-specific and must be replaced/adapted on desktop.

### Portable, but need small edits (NOT copy-as-is)
- `data/model/Models.kt` — `SelectedFileInfo.uri: android.net.Uri` (line 21) is an Android type.
  For desktop change it to a `String` path or `java.io.File`; this ripples into `SendScreen`
  `SelectedFileRow` and the ViewModel's file-handling glue.
- `ui/components/DeviceCard.kt`, `DiscoveryRadar.kt`, `FileTransferItem.kt` — each has ONE
  `androidx.compose.ui.tooling.preview.Preview` import (and matching `@Preview` functions) which
  Compose Desktop (JVM) does not provide. Drop those imports + the `@Preview`-annotated functions.

### Fully portable (copy as-is into desktop project)
- `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`
- `ui/screens/SendScreen.kt`, `ui/screens/ReceiveScreen.kt`
- `ui/components/SettingsSheet.kt`, `TransferCard.kt` (the two without Preview imports)

### Near-portable (tiny adapt: remove `android.util.Log` / `Context` usages)
- `data/network/FileTransfer.kt` — core protocol, TLS (SSLContext + BouncyCastle cert gen),
  `closeAfterSend`, framing, PIN. JVM sockets already portable. No WiFi Direct dependency.
- `data/network/DeviceDiscovery.kt` — only uses `android.util.Log`; swap for java `println`/logger.

### Android-specific (must reimplement or drop on desktop)
- `presentation/MainViewModel.kt` — heavy `ContentResolver`/SAF/`Context`; desktop reimplements
  file picking via Swing/AWT FileDialog or JFileChooser; keep the send/receive state + progress
  logic (portable parts in `FileTransfer.kt`).
- `service/ReceiveService.kt` — Android foreground service; desktop reimplements a plain
  long-running thread/coroutine that calls the same `FileTransfer` receive functions.
- `data/local/FileStorage.kt` — `ContentResolver`; desktop uses `java.io.File` paths.
- `data/network/WifiDirectManager.kt` — **N/A on Windows** (WiFi Direct is Android-only). Use
  manual IP (already exists in `SendScreen`) and/or TCP port-scan based `DeviceDiscovery` instead.
- `MainActivity.kt` — replaced by a Compose Desktop `main()` + `Window`.
- `data/network/QrUtils.kt` — QR scanning not needed on desktop (no camera); skip or use manual IP.

## Recommended technical approach (Compose Multiplatform setup)
1. New Gradle Kotlin project (NOT inside the Android app `:app` module) with:
   - `org.jetbrains.compose` plugin + `org.jetbrains.kotlin.multiplatform` (jvm() target only is
     sufficient for Windows desktop), or the simpler `org.jetbrains.compose.desktop.application`.
   - Compose Desktop `application { mainClass = "...MainKt" }` Gradle task → produces an
     installable/launchable app; use `jpackageImage`/`packageDistributionForCurrentOS` for a Windows
     installer, plus a code-signing cert to avoid SmartScreen warnings (out of scope here).
2. `commonMain`-style source layout OR reuse files directly in a plain `src/main/kotlin`.
   - Simpler: plain JVM module, copy the 2 fully-portable screen files + 2 portable components +
     the 3 theme files, adapt Models.kt + the 3 Preview-import components, adapt the 2
     near-portable network files, then write a new desktop `Main.kt`, `DesktopFilePicker`,
     `DesktopReceiverService`, `DesktopViewModel`.
3. Reuse `FileTransfer.PORT = 5001`, framing, `BUFFER_SIZE`, TLS `closeAfterSend` unchanged so the
   desktop sender/receiver interoperates 1:1 with the Android build.

## Deliverables for the new session
- Copy the fully-portable files; adapt `Models.kt` (Uri), the 3 Preview-import components, and the 2
  network files as described above.
- New `Main.kt` (Compose `Window`, reuse the slate/emerald `Theme.kt`).
- Desktop file picker (JFileChooser) wired into `SendScreen.onPickFiles`.
- Desktop "Receive" implemented as a background thread using `FileTransfer` receive helpers;
  store files to a chosen directory.
- Run and interop-test desktop↔Android both directions over the same protocol.

## Notes / risks
- Match the Android color theme exactly (Slate `#0F172A`/`#1E293B` bg, Emerald `#10B981` primary,
  muted `#94A3B8`) by reusing `Color.kt`/`Theme.kt` verbatim.
- Desktop has no camera/QR, no WiFi Direct, no foreground service — acceptable per scope.
- Pending CRITICAL BUG not related to this plan: Android→PC large-file truncation at integral 256KB
  boundaries. `FileTransfer.closeAfterSend` (flush + 200ms drain before close) is the current fix,
  APK built & installed 8/30. Verify that too before finalizing desktop interop.
