package com.transithub.shared.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.transithub.shared.id.UuidV7;

/**
 * Files on local disk, encrypted with AES-256-GCM.
 *
 * <p>Each file is written as {@code [12-byte random nonce][ciphertext + tag]}.
 * GCM also detects tampering: a modified file fails to decrypt instead of
 * returning altered content. The key comes from configuration
 * ({@code TRANSITHUB_STORAGE_KEY}, base64 of 32 random bytes) and must
 * never be committed.
 *
 * <p>Used in development and tests, and suitable for a single server. The
 * production store at Infratel may replace it behind {@link FileStorage}.
 */
@Component
@EnableConfigurationProperties(EncryptedLocalFileStorage.Settings.class)
public class EncryptedLocalFileStorage implements FileStorage {

    @ConfigurationProperties("transithub.storage")
    record Settings(String root, String encryptionKey) {
    }

    private static final Pattern KEY_FORMAT = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Path root;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    EncryptedLocalFileStorage(Settings settings) {
        if (settings.root() == null || settings.root().isBlank()) {
            throw new IllegalStateException("transithub.storage.root must be set");
        }
        this.root = Path.of(settings.root()).toAbsolutePath().normalize();
        this.key = decodeKey(settings.encryptionKey());
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create file storage at " + root, e);
        }
    }

    @Override
    public String store(byte[] content) {
        String storageKey = UuidV7.generate().toString();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(storageKey.getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted = cipher.doFinal(content);

            Path file = pathOf(storageKey);
            Files.createDirectories(file.getParent());
            Files.write(file, ByteBuffer.allocate(NONCE_BYTES + encrypted.length)
                    .put(nonce).put(encrypted).array());
            return storageKey;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write stored file", e);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            byte[] stored = Files.readAllBytes(pathOf(storageKey));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
            // Binding the key as associated data stops one file being swapped for another.
            cipher.updateAAD(storageKey.getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
        } catch (NoSuchFileException e) {
            throw new StoredFileNotFoundException(storageKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Stored file " + storageKey + " failed integrity check", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read stored file", e);
        }
    }

    /** Keys are our own UUIDs; anything else is rejected before touching the disk. */
    private Path pathOf(String storageKey) {
        if (!KEY_FORMAT.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        // Spread files over 256 folders by the key's last two characters.
        return root.resolve(storageKey.substring(storageKey.length() - 2)).resolve(storageKey + ".bin");
    }

    private static SecretKey decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "transithub.storage.encryption-key (TRANSITHUB_STORAGE_KEY) must be set: base64 of 32 random bytes");
        }
        byte[] bytes = Base64.getDecoder().decode(encoded.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException("The storage encryption key must be 32 bytes (AES-256)");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    @Override
    public String toString() {
        return "EncryptedLocalFileStorage[" + root + "]";
    }
}
