package com.offlinepay.app.data.crypto

import android.util.Base64
import com.offlinepay.app.data.model.PaymentToken
import com.offlinepay.app.data.model.TokenPayload
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenSigner @Inject constructor(
    private val keyManager: KeyManager,
    moshi: Moshi
) {
    private val payloadAdapter: JsonAdapter<TokenPayload> =
        moshi.adapter(TokenPayload::class.java)

    fun sign(payload: TokenPayload): PaymentToken {
        val payloadJson = payloadAdapter.toJson(payload)
        val sigBytes = Signature.getInstance(KeyManager.SIG_ALGO).run {
            initSign(keyManager.privateKey())
            update(payloadJson.toByteArray(Charsets.UTF_8))
            sign()
        }
        return PaymentToken(
            payloadJson = payloadJson,
            signature = Base64.encodeToString(sigBytes, Base64.NO_WRAP),
            senderPublicKey = Base64.encodeToString(
                keyManager.publicKey().encoded, Base64.NO_WRAP
            )
        )
    }

    /** Returns the parsed payload only if the signature is valid. */
    fun verify(token: PaymentToken): TokenPayload? = try {
        val pubBytes = Base64.decode(token.senderPublicKey, Base64.NO_WRAP)
        val pub: PublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(pubBytes))
        val ok = Signature.getInstance(KeyManager.SIG_ALGO).run {
            initVerify(pub)
            update(token.payloadJson.toByteArray(Charsets.UTF_8))
            verify(Base64.decode(token.signature, Base64.NO_WRAP))
        }
        if (ok) payloadAdapter.fromJson(token.payloadJson) else null
    } catch (_: Throwable) {
        null
    }
}
