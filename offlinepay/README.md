# OfflinePay — Offline-First P2P Payment App (Android, Kotlin)

A working prototype of an offline-first peer-to-peer payment app. Transactions
are created and transferred without internet (QR / Bluetooth / SMS) using
cryptographically signed tokens, then settled automatically with a backend
when connectivity returns.

## Stack

- **Language:** Kotlin 1.9.22
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM + Clean Architecture, Hilt DI
- **Persistence:** Room
- **Background sync:** WorkManager + ConnectivityManager listener
- **Crypto:** Android Keystore (EC P-256, SHA256withECDSA)
- **QR:** ZXing core + journeyapps embedded scanner
- **Bluetooth:** Classic RFCOMM (working host + client demo)
- **Networking:** Retrofit + OkHttp + Moshi
- **Mock backend:** Node.js Express in `/mock-backend`

Min SDK 26 · Target SDK 34 · Gradle 8.6 · AGP 8.3.2 · JDK 17

## Quick start

### 1. Open the app in Android Studio

1. Unzip and open the **project root folder** in Android Studio Hedgehog (or
   newer).
2. When prompted, accept the JDK 17 toolchain. Let Gradle sync.
   The bundled wrapper (`gradlew`, `gradle-wrapper.jar`) means no manual
   Gradle install is required.
3. Run the `app` configuration on a real device (preferred — needs camera +
   Bluetooth) or the emulator.

### 2. Run the mock backend

```bash
cd mock-backend
npm install
npm start          # listens on 0.0.0.0:8080
```

- **Emulator** → leave `BASE_URL = "http://10.0.2.2:8080/"` in
  `app/src/main/java/com/offlinepay/app/di/NetworkModule.kt` (default).
- **Real device** → change it to your laptop's LAN IP, e.g.
  `http://192.168.1.10:8080/`. Make sure your firewall allows port 8080 and
  the device is on the same Wi-Fi.

That's it. The app will work fully offline; sync happens automatically
whenever an internet-capable network appears.

## Project layout

```
.
├── build.gradle.kts          # root build
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/           # Gradle 8.6 wrapper (jar included)
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/              # icons, themes, strings, backup rules
│       └── java/com/offlinepay/app/
│           ├── OfflinePayApp.kt          # Hilt + WorkManager configuration
│           ├── ui/MainActivity.kt
│           ├── ui/screens/               # Home, Send, ShowQr, Receive, Bluetooth, History
│           ├── ui/theme/Theme.kt
│           ├── data/
│           │   ├── crypto/   KeyManager (Android Keystore), TokenSigner
│           │   ├── db/       Room AppDatabase + DAO
│           │   ├── model/    PaymentToken, TransactionEntity
│           │   ├── remote/   Retrofit SettlementApi
│           │   └── repo/     PaymentRepository (single source of truth)
│           ├── sync/         SettlementWorker (WorkManager) + ConnectivityObserver
│           ├── bluetooth/    BluetoothPeer (RFCOMM host + client)
│           ├── qr/           QrCodec (ZXing encode)
│           ├── di/           Hilt modules (App, Network)
│           └── util/         Money formatting helpers
└── mock-backend/
    ├── package.json
    └── server.js
```

## How offline-first works

1. **Create payment (offline).** Sender enters recipient + amount. App builds
   `TokenPayload`, serializes to JSON, signs the exact UTF-8 bytes with an EC
   P-256 key in Android Keystore. Result is wrapped in `PaymentToken`
   `{ payloadJson, signature, senderPublicKey }` and persisted as
   `PENDING_SETTLEMENT`.
2. **Transfer (offline).** Token JSON is shown as a QR code, broadcast over
   Bluetooth RFCOMM, or copied as an SMS body.
3. **Receive (offline).** Receiver scans/receives. Signature is verified
   against the embedded sender public key on the **exact bytes** that were
   signed (no canonical-JSON guesswork). If valid and not expired, the
   transaction details are shown for accept/reject. Accepted → status
   `PENDING_SETTLEMENT`.
4. **Settle (online).** A `ConnectivityObserver` registered on the default
   network triggers `SettlementWorker.enqueue()` the moment internet appears.
   The worker `POST /settle`s every pending transaction. Backend re-verifies
   the signature, rejects expired tokens, dedupes by `txId`, marks
   `COMPLETED`.
5. **Failure.** WorkManager retries with exponential backoff (30 s base).
   `PENDING_SETTLEMENT` → `SYNCING` → `COMPLETED` / `FAILED` is reflected in
   the History screen.

## Security

- Private key is generated and used inside the **Android Keystore** — it is
  never exported and may be hardware-backed (StrongBox / TEE) on supporting
  devices.
- Tokens are signed with `SHA256withECDSA` over the exact serialized payload
  bytes — eliminates any client/server canonicalization mismatch.
- Receiver and backend both re-verify before any state change.
- Replay protection: per-token `nonce`, 5-minute TTL, server-side `txId`
  dedupe.

## Testing the offline + sync flow

### Two-emulator (or two-device) demo

1. Start the mock backend: `cd mock-backend && npm start`.
2. Install the app on **two** emulators / devices (call them **A** and **B**).
3. On **both**, switch to airplane mode (Extended Controls → Cellular on the
   emulator).
4. **A**: Send payment → enter recipient (e.g. `+919999999999`) and amount →
   **Generate QR**. The QR is now on screen.
5. **B**: Receive (scan QR) → grant camera → scan A's screen → **Accept**.
   History on both shows `PENDING_SETTLEMENT`.
6. Re-enable connectivity on either device. Within ~30 s the
   `SettlementWorker` runs and the row flips to `COMPLETED`. Verify on the
   backend with `curl http://localhost:8080/ledger`.

### Single-device demo (no second phone)

- After **Generate QR**, screenshot it (`adb exec-out screencap -p > qr.png`),
  open on a different display, scan with the same app's Receive flow.

### Negative tests

- **Duplicate**: scan the same QR twice. Second attempt → `Duplicate
  transaction` (client) or backend returns `DUPLICATE` (idempotent).
- **Expired**: wait > 5 min before scanning → client shows `Token expired`.
- **Tampered**: change one byte of the QR text manually — signature
  verification fails before any UI prompt.

### Bluetooth working demo

1. **Pair** the two devices via system Settings → Bluetooth (one-time).
2. On **A**: Send payment → Generate QR → tap **Send via Bluetooth** (or open
   the Bluetooth tab from Home) → grant permissions → tap **Host (broadcast
   last generated token)**. A is now waiting for an inbound RFCOMM
   connection.
3. On **B**: open the Bluetooth tab → grant permissions → tap A in the bonded
   devices list. The token streams over RFCOMM, the signature is verified,
   and B saves it as `PENDING_SETTLEMENT`.
4. Re-enable internet — same WorkManager sync as the QR flow.

> Permissions: on Android 12+ the app requests `BLUETOOTH_CONNECT` and
> `BLUETOOTH_SCAN`; on older versions, `BLUETOOTH`, `BLUETOOTH_ADMIN`, and
> `ACCESS_FINE_LOCATION` (needed for discovery).

## Mock backend API

| Method | Path     | Body                          | Response                                              |
|--------|----------|-------------------------------|-------------------------------------------------------|
| POST   | /settle  | `{ tokenJson: "<json>" }`     | `{ ok, txId, status: COMPLETED\|DUPLICATE\|EXPIRED\|INVALID, message? }` |
| GET    | /ledger  | —                             | `[ { ...payload, settledAt } ]`                       |
| GET    | /health  | —                             | `{ ok: true }`                                        |

Signature is verified with Node's built-in `crypto.createVerify("SHA256")` on
the exact `payloadJson` bytes using the embedded EC P-256 SPKI public key.

## Known prototype limitations

- **Public-key trust**: the receiver verifies with the pubkey embedded in the
  token. This proves the token was signed by *some* key — not that the key
  belongs to the claimed `senderId`. Production: fetch the sender's pubkey by
  phone-number hash from a signed directory.
- **No real PSP**: the backend logs and acks. Plug a real UPI / card / wallet
  provider into the `POST /settle` handler.
- **Bluetooth pairing is manual**: we use only bonded devices. For unpaired
  discovery, add `startDiscovery()` and a `BroadcastReceiver` for `ACTION_FOUND`.
- **NFC, contact picker, FCM push**: stubs / not implemented.

## Troubleshooting

- **"Could not resolve com.journeyapps:zxing-android-embedded"**: ensure
  `mavenCentral()` is in `settings.gradle.kts` (it is, by default in this
  project).
- **Camera scanner crashes with `Theme.AppCompat` error**: already fixed by
  the explicit `androidx.appcompat:appcompat` dependency.
- **App can't reach the backend on a real device**: you're using `10.0.2.2`,
  which only works on the emulator. Switch `BASE_URL` to your LAN IP.
- **Sync never runs**: check Android's battery optimization isn't blocking
  background work for the app. Force a run with
  `adb shell cmd jobscheduler run -f com.offlinepay.app 999` or simply tap
  Home → re-toggle Wi-Fi.
