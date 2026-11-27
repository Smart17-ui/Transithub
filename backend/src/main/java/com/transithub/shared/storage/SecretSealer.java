package com.transithub.shared.storage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Encrypts short secrets (e.g. an invitation token waiting in the
 * notification outbox) so a database reader or backup cannot use them.
 * AES-256-GCM with the storage key; the result is base64 text.
 */
@Component
public class SecretSealer {

    private static final byte[] PURPOSE = "transithub-sealed-secret-v1".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES = 12;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    SecretSealer(EncryptedLocalFileStorage.Settings settings) {
        byte[] bytes = Base64.getDecoder().decode(settings.encryptionKey().trim());
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String seal(String secret) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(PURPOSE);
            byte[] sealed = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Sealing failed", e);
        }
    }

    /** For the notification sender: the secret, just before delivery. */
    public String unseal(String sealed) {
        try {
            byte[] bytes = Base64.getDecoder().decode(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, bytes, 0, NONCE_BYTES));
            cipher.updateAAD(PURPOSE);
            return new String(cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Sealed value failed its integrity check", e);
        }
    }
}
