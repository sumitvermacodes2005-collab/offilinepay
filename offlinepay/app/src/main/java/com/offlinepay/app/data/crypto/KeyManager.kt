package com.offlinepay.app.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Android Keystore. Generates an EC P-256 key pair on first use and
 * exposes the public key for embedding in payment tokens. The private key
 * NEVER leaves the secure hardware-backed keystore.
 */
@Singleton
class KeyManager @Inject constructor() {

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "offlinepay_signing_key_v1"
        const val SIG_ALGO = "SHA256withECDSA"
    }

    private val ks: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }
    }

    fun ensureKeyPair() {
        if (ks.containsAlias(ALIAS)) return
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        val kpg = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, KEYSTORE
        )
        kpg.initialize(spec)
        kpg.generateKeyPair()
    }

    fun privateKey(): PrivateKey {
        ensureKeyPair()
        return (ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry).privateKey
    }

    fun publicKey(): PublicKey {
        ensureKeyPair()
        return ks.getCertificate(ALIAS).publicKey
    }
}
