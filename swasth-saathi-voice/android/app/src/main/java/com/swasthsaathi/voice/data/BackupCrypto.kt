package com.swasthsaathi.voice.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional cloud backup. The notebook is encrypted ON DEVICE with a key derived from the user's
 * passphrase, so the cloud (Drive, S3, your server) only ever stores ciphertext. Lose the passphrase
 * and the backup is unrecoverable. That is the point; tell the user clearly.
 * Format: base64( salt[16] || iv[12] || ciphertext )
 */
object BackupCrypto {
    private const val ITER = 210_000

    fun seal(plaintext: String, passphrase: CharArray): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes); val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv)) }
        return Base64.encodeToString(salt + iv + c.doFinal(plaintext.toByteArray()), Base64.NO_WRAP)
    }

    fun open(blob: String, passphrase: CharArray): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val salt = raw.copyOfRange(0, 16); val iv = raw.copyOfRange(16, 28)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv)) }
        return String(c.doFinal(raw, 28, raw.size - 28))
    }

    private fun key(pass: CharArray, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass, salt, ITER, 256)).encoded, "AES")
}
