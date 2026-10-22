package team.sopa.boar.share

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The key a shared evaluation run is signed with (modules/device-key's DeviceKeyModule.kt, same alias, so a key made by
 * the Expo app keeps working after the upgrade). Made inside the phone's secure hardware (StrongBox where there is
 * one, else the TEE); its attestation chain, signed by Google's root, tells the server the key is real hardware and
 * was made for this app, signed with BOAR's release key.
 */
object DeviceKey {
    private const val ALIAS = "boar-share-v1"

    /** The secure hardware refused to attest (some genuine phones): fall back to [createUnattested]. */
    class AttestationFailed(message: String, cause: Throwable?) : Exception(message, cause)

    class Created(val attestation: List<String>, val publicKey: String)

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun has(): Boolean = keyStore().containsAlias(ALIAS)

    fun delete() { val ks = keyStore(); if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS) }

    /** A new key whose attestation carries the server's challenge; the chain, leaf first. */
    fun create(challenge: String): Created {
        val ks = keyStore()
        try { generateWithFallback(ks, challenge) } catch (e: Exception) { throw AttestationFailed("The secure hardware couldn't attest a key: ${describe(e)}", e) }
        val chain = ks.getCertificateChain(ALIAS)
        if (chain == null || chain.size < 2) throw AttestationFailed("The new key has no attestation chain", null)
        return Created(chain.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }, Base64.encodeToString(chain[0].publicKey.encoded, Base64.NO_WRAP))
    }

    /** A key without attestation, for a phone that can't attest: its shares wait for review. */
    fun createUnattested(): Created {
        val ks = keyStore()
        generateWithFallback(ks, null)
        val cert = ks.getCertificate(ALIAS) ?: error("The new key has no public key")
        return Created(emptyList(), Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP))
    }

    /** ECDSA P-256 / SHA-256 signature (DER, base64) of the UTF-8 bytes of [data]. */
    fun sign(data: String): String {
        val key = keyStore().getKey(ALIAS, null) as? PrivateKey ?: error("No device key yet")
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(key); update(data.toByteArray(Charsets.UTF_8)); sign() }
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    private fun generateWithFallback(ks: KeyStore, challenge: String?) {
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        // StrongBox is missing on most phones and fails on some that claim it: the TEE does the job either way.
        try {
            generate(challenge, strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        } catch (e: Exception) {
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
            generate(challenge, strongBox = false)
        }
    }

    private fun generate(challenge: String?, strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply { if (challenge != null) setAttestationChallenge(challenge.toByteArray(Charsets.UTF_8)) }
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run { initialize(spec); generateKeyPair() }
    }

    private fun describe(e: Throwable): String =
        generateSequence(e) { it.cause }.take(3).joinToString(" / ") { "${it.javaClass.simpleName}: ${it.message}" }.take(300)
}
