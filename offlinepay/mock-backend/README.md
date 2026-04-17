# OfflinePay Mock Settlement Backend

```bash
npm install
npm start         # listens on :8080
```

Endpoints:
- `POST /settle` — body `{ "tokenJson": "<json>" }` — verifies signature,
  checks expiry & duplicates, returns `{ ok, txId, status, message? }`.
- `GET /ledger`  — view settled transactions (debug).
- `GET /health`  — liveness.

When testing on the Android **emulator**, leave `BASE_URL` as
`http://10.0.2.2:8080/` in `NetworkModule.kt`. On a **real device**, change it
to your dev machine's LAN IP (e.g. `http://192.168.1.10:8080/`) and ensure
firewall allows port 8080.
