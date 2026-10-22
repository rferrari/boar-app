package team.sopa.boar.engine.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** How chat text is stored: as is, or sealed with the PIN's data key. */
interface TextCipher {
    fun seal(plain: String): String
    fun open(stored: String): String
}

/** No PIN: stored as written. Opening still reads text sealed earlier as-is (it can't, so it shows a placeholder). */
object PlainCipher : TextCipher {
    override fun seal(plain: String) = plain
    override fun open(stored: String) = if (AesTextCipher.isSealed(stored)) AesTextCipher.LOCKED else stored
}

/**
 * AES-256-GCM with a random 12-byte nonce per value, stored as "enc1:<base64(nonce|ciphertext)>". Text written before
 * the PIN was set (no prefix) opens as is, so turning the PIN on can re-seal rows one at a time.
 */
class AesTextCipher(key: ByteArray) : TextCipher {
    private val key = SecretKeySpec(key.copyOf(), "AES")
    private val random = SecureRandom()

    override fun seal(plain: String): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
        return PREFIX + Base64.getEncoder().encodeToString(nonce + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    override fun open(stored: String): String {
        if (!isSealed(stored)) return stored
        return try {
            val b = Base64.getDecoder().decode(stored.substring(PREFIX.length))
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b, 0, 12)) }
            String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            LOCKED
        }
    }

    companion object {
        const val PREFIX = "enc1:"
        /** What a sealed value shows when it can't be opened (wrong key, or no PIN entered). */
        const val LOCKED = "🔒"
        fun isSealed(s: String?) = s != null && s.startsWith(PREFIX)
    }
}

/**
 * The PIN's keys. A random data key encrypts the chats; it is stored sealed with a key derived from the PIN
 * (PBKDF2-HMAC-SHA256), so the PIN never needs to be stored and changing it only re-seals the data key. The app adds a
 * second seal with a non-exportable Android Keystore key, so a copy of the files can't be brute-forced off the phone.
 */
object PinKeys {
    const val ITERATIONS = 210_000
    private val random = SecureRandom()

    fun newDataKey(): ByteArray = ByteArray(32).also(random::nextBytes)
    fun newSalt(): ByteArray = ByteArray(16).also(random::nextBytes)

    fun derive(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded

    fun wrap(dataKey: ByteArray, kek: ByteArray): ByteArray {
        val nonce = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, nonce)) }
        return nonce + c.doFinal(dataKey)
    }

    /** The data key, or null when [kek] is wrong (a wrong PIN). */
    fun unwrap(wrapped: ByteArray, kek: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, wrapped, 0, 12)) }
        c.doFinal(wrapped, 12, wrapped.size - 12)
    } catch (e: AEADBadTagException) {
        null
    }

    /** 4 to 8 digits. */
    fun valid(pin: String) = pin.length in 4..8 && pin.all { it in '0'..'9' }

    /** Seconds to wait before the next try after [failures] wrong PINs: none for the first 4, then 30 s doubling, at most 1 h. */
    fun backoffSeconds(failures: Int): Long = if (failures < 5) 0 else minOf(3600L, 30L shl minOf(failures - 5, 7))
}
