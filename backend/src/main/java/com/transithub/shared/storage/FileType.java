package com.transithub.shared.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * File types accepted as evidence, recognised from the file's first bytes
 * ("magic numbers"), never from its name or the browser's declared type.
 * A renamed executable is therefore refused (AC-R1-CAP-06).
 */
public enum FileType {

    PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String mediaType;
    private final byte[] signature;

    FileType(String mediaType, byte[] signature) {
        this.mediaType = mediaType;
        this.signature = signature;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<FileType> detect(byte[] content) {
        return Arrays.stream(values())
                .filter(type -> content.length >= type.signature.length
                        && Arrays.equals(content, 0, type.signature.length,
                        type.signature, 0, type.signature.length))
                .findFirst();
    }
}
