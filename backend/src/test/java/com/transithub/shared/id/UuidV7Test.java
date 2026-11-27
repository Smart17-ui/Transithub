package com.transithub.shared.id;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidV7Test {

    @Test
    void generatesVersion7WithIetfVariant() {
        UUID uuid = UuidV7.generate();

        assertEquals(7, uuid.version());
        assertEquals(2, uuid.variant());
    }

    @Test
    void embedsTheCurrentTime() {
        long before = System.currentTimeMillis();
        UUID uuid = UuidV7.generate();
        long after = System.currentTimeMillis();

        long timestamp = UuidV7.timestampOf(uuid);
        // The counter may borrow a millisecond under heavy load, hence +1.
        assertTrue(timestamp >= before && timestamp <= after + 1,
                "timestamp " + timestamp + " not within [" + before + ", " + after + "]");
    }

    @Test
    void isUniqueAndStrictlyIncreasingWithinOneProcess() {
        List<UUID> generated = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            generated.add(UuidV7.generate());
        }

        assertEquals(generated.size(), new HashSet<>(generated).size(), "duplicates found");

        for (int i = 1; i < generated.size(); i++) {
            UUID previous = generated.get(i - 1);
            UUID current = generated.get(i);
            // Compare the time-ordered high bits as unsigned values.
            assertTrue(Long.compareUnsigned(
                            current.getMostSignificantBits(),
                            previous.getMostSignificantBits()) > 0,
                    "not increasing at index " + i);
        }
    }

    @Test
    void rejectsTimestampLookupForOtherVersions() {
        assertThrows(IllegalArgumentException.class,
                () -> UuidV7.timestampOf(UUID.randomUUID()));
    }
}
