/**
 * OfflinePay mock settlement backend.
 *
 *   POST /settle   { tokenJson: "<json>" }
 *      -> { ok, txId, status: COMPLETED|DUPLICATE|EXPIRED|INVALID, message? }
 *   GET  /ledger   -> all settled transactions (debug)
 *   GET  /health   -> { ok: true }
 *
 * Token format ({@code PaymentToken}) embeds the EXACT bytes that were signed
 * as `payloadJson`. We verify the signature against those bytes directly,
 * eliminating any canonical-JSON divergence between client and server.
 *
 * Signature: SHA256withECDSA, P-256 (secp256r1).
 * Public key: X.509 SubjectPublicKeyInfo, base64.
 */
const express = require("express");
const crypto = require("crypto");

const app = express();
app.use(express.json({ limit: "256kb" }));

const seenTxIds = new Set();   // duplicate detection (in-memory; restart clears)
const ledger = [];

function verifySignature(token) {
  try {
    const spki = Buffer.from(token.senderPublicKey, "base64");
    const pubKey = crypto.createPublicKey({
      key: spki, format: "der", type: "spki",
    });
    const verifier = crypto.createVerify("SHA256");
    verifier.update(Buffer.from(token.payloadJson, "utf8"));
    verifier.end();
    return verifier.verify(
      { key: pubKey, dsaEncoding: "der" },
      Buffer.from(token.signature, "base64")
    );
  } catch (e) {
    console.warn("verify error:", e.message);
    return false;
  }
}

app.post("/settle", (req, res) => {
  let token;
  try { token = JSON.parse(req.body.tokenJson); }
  catch (_) {
    return res.json({ ok: false, txId: "", status: "INVALID", message: "bad json" });
  }
  if (!token || !token.payloadJson || !token.signature || !token.senderPublicKey) {
    return res.json({ ok: false, txId: "", status: "INVALID", message: "missing fields" });
  }

  if (!verifySignature(token)) {
    return res.json({ ok: false, txId: "", status: "INVALID", message: "bad signature" });
  }

  let p;
  try { p = JSON.parse(token.payloadJson); }
  catch (_) {
    return res.json({ ok: false, txId: "", status: "INVALID", message: "bad payload" });
  }
  if (!p.txId || !p.senderId || !p.receiverId || typeof p.amountMinor !== "number") {
    return res.json({ ok: false, txId: p.txId || "", status: "INVALID", message: "bad payload" });
  }
  if (Date.now() > p.expiresAtMillis) {
    return res.json({ ok: false, txId: p.txId, status: "EXPIRED" });
  }
  if (seenTxIds.has(p.txId)) {
    return res.json({ ok: true, txId: p.txId, status: "DUPLICATE" });
  }

  seenTxIds.add(p.txId);
  ledger.push({ ...p, settledAt: Date.now() });
  console.log(`[settle] ${p.senderId} -> ${p.receiverId}  ${p.amountMinor} ${p.currency}  (${p.txId})`);
  res.json({ ok: true, txId: p.txId, status: "COMPLETED" });
});

app.get("/ledger", (_, res) => res.json(ledger));
app.get("/health", (_, res) => res.json({ ok: true }));

const PORT = process.env.PORT || 8080;
app.listen(PORT, "0.0.0.0", () => console.log(`OfflinePay mock backend on :${PORT}`));
