package com.offlinepay.app.data.model

import com.squareup.moshi.JsonClass

/**
 * Wire format for an offline-signed payment intent.
 *
 * `payloadJson` is the EXACT UTF-8 bytes that were signed (we keep it as a
 * string so we never have to re-canonicalize on the verifier side). Both the
 * Android receiver and the mock backend verify `signature` against
 * `payloadJson.getBytes(UTF_8)` and then parse `payloadJson` into [TokenPayload].
 *
 * `signature`        = base64( SHA256withECDSA( payloadJson UTF-8 bytes ) )
 * `senderPublicKey`  = base64( X.509 SubjectPublicKeyInfo of sender's EC P-256 key )
 */
@JsonClass(generateAdapter = true)
data class PaymentToken(
    val payloadJson: String,
    val signature: String,
    val senderPublicKey: String
)

@JsonClass(generateAdapter = true)
data class TokenPayload(
    val txId: String,
    val senderId: String,
    val receiverId: String,
    val amountMinor: Long,
    val currency: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val nonce: String
)
