package com.transithub.shared.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Evidence is encrypted at rest and tamper-evident (Release 1 spec section 6). */
class EncryptedLocalFileStorageTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @TempDir
    Path root;

    @Test
    void storedContentReadsBackExactly() {
        FileStorage storage = storage();
        byte[] content = "%PDF-1.7 certificate of incorporation".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(content, storage.read(storage.store(content)));
    }

    @Test
    void filesOnDiskAreEncrypted() throws Exception {
        FileStorage storage = storage();
        String secret = "NRC 123456/10/1 belongs to Mr Banda";
        storage.store(secret.getBytes(StandardCharsets.UTF_8));

        try (Stream<Path> files = Files.walk(root)) {
            Path file = files.filter(Files::isRegularFile).findFirst().orElseThrow();
            String onDisk = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            assertFalse(onDisk.contains("Banda"), "plain text must never reach the disk");
        }
    }

    @Test
    void tamperedFilesAreDetected() throws Exception {
        FileStorage storage = storage();
        String key = storage.store("original".getBytes(StandardCharsets.UTF_8));

        try (Stream<Path> files = Files.walk(root)) {
            Path file = files.filter(Files::isRegularFile).findFirst().orElseThrow();
            byte[] bytes = Files.readAllBytes(file);
            bytes[bytes.length - 1] ^= 1;
            Files.write(file, bytes);
        }

        assertThrows(IllegalStateException.class, () -> storage.read(key));
    }

    @Test
    void aWrongKeyCannotRead() {
        String stored = storage().store("secret".getBytes(StandardCharsets.UTF_8));
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        FileStorage other = new EncryptedLocalFileStorage(new EncryptedLocalFileStorage.Settings(
                root.toString(), Base64.getEncoder().encodeToString(otherKey)));

        assertThrows(IllegalStateException.class, () -> other.read(stored));
    }

    @Test
    void keysFromOutsideTheStoreAreRejected() {
        FileStorage storage = storage();

        assertThrows(IllegalArgumentException.class, () -> storage.read("../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> storage.read("C:\\Windows\\win.ini"));
    }

    @Test
    void unknownKeysAreReportedAsMissing() {
        assertThrows(FileStorage.StoredFileNotFoundException.class,
                () -> storage().read("01234567-89ab-7cde-8f01-23456789abcd"));
    }

    @Test
    void refusesToStartWithoutAProperKey() {
        assertThrows(IllegalStateException.class, () -> new EncryptedLocalFileStorage(
                new EncryptedLocalFileStorage.Settings(root.toString(), "")));
        assertThrows(IllegalStateException.class, () -> new EncryptedLocalFileStorage(
                new EncryptedLocalFileStorage.Settings(root.toString(),
                        Base64.getEncoder().encodeToString(new byte[16]))));
    }

    @Test
    void fileTypesAreRecognisedFromTheirBytes() {
        assertEquals(FileType.PDF, FileType.detect("%PDF-1.4".getBytes(StandardCharsets.US_ASCII)).orElseThrow());
        assertEquals(FileType.PNG, FileType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0}).orElseThrow());
        assertEquals(FileType.JPEG, FileType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}).orElseThrow());
        assertFalse(FileType.detect("MZ executable renamed to .pdf".getBytes(StandardCharsets.US_ASCII)).isPresent());
    }

    private FileStorage storage() {
        return new EncryptedLocalFileStorage(new EncryptedLocalFileStorage.Settings(root.toString(), KEY));
    }
}
