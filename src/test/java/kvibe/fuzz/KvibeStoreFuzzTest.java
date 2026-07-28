package kvibe.fuzz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import kvibe.KvibeStore;
import kvibe.StoreConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TR-4: flip a single random bit at a random position of an otherwise-valid data file and check
 * that {@code open()} either recovers (with truncation) or fails with a clear {@link IOException},
 * never with {@link BufferUnderflowException}, {@link NegativeArraySizeException} or {@link
 * OutOfMemoryError} — and that records entirely before the corrupted byte remain readable.
 */
class KvibeStoreFuzzTest {

    private static final int RECORD_COUNT = 20;
    private static final int ITERATIONS = 300;

    @TempDir
    Path dir;

    @Test
    void singleBitFlipNeverCrashesOpenAndNeverCorruptsDataBeforeIt() throws IOException {
        Path referencePath = dir.resolve("reference.kvibe");
        List<byte[]> keys = new ArrayList<>();
        List<byte[]> values = new ArrayList<>();
        List<Long> recordEndOffsets = new ArrayList<>();

        try (KvibeStore store = KvibeStore.open(referencePath, StoreConfig.defaults())) {
            for (int i = 0; i < RECORD_COUNT; i++) {
                byte[] key = ("key-" + i).getBytes(StandardCharsets.UTF_8);
                byte[] value = ("value-" + i).getBytes(StandardCharsets.UTF_8);
                store.put(key, value);
                keys.add(key);
                values.add(value);
                recordEndOffsets.add(Files.size(referencePath));
            }
        }
        byte[] referenceBytes = Files.readAllBytes(referencePath);

        long seed = new Random().nextLong();
        Random rnd = new Random(seed);

        for (int iter = 0; iter < ITERATIONS; iter++) {
            int position = rnd.nextInt(referenceBytes.length);
            int bit = rnd.nextInt(8);

            byte[] corrupted = referenceBytes.clone();
            corrupted[position] ^= (byte) (1 << bit);
            Path corruptPath = dir.resolve("corrupt-" + iter + ".kvibe");
            Files.write(corruptPath, corrupted);

            try {
                verifyOneCorruption(corruptPath, position, keys, values, recordEndOffsets);
            } catch (AssertionError e) {
                throw new AssertionError(
                        "fuzz iteration %d failed (position=%d, bit=%d, seed=%d)".formatted(iter, position, bit, seed),
                        e);
            }
        }
    }

    private void verifyOneCorruption(
            Path corruptPath, int position, List<byte[]> keys, List<byte[]> values, List<Long> recordEndOffsets)
            throws IOException {
        KvibeStore store;
        try {
            store = KvibeStore.open(corruptPath, StoreConfig.defaults());
        } catch (BufferUnderflowException | NegativeArraySizeException | OutOfMemoryError forbidden) {
            throw new AssertionError("open() leaked a forbidden exception type: " + forbidden, forbidden);
        } catch (IOException expectedFailureMode) {
            // open() is allowed to refuse a file it can't safely recover (e.g. a corrupted header).
            return;
        }

        try {
            for (int i = 0; i < keys.size(); i++) {
                if (recordEndOffsets.get(i) <= position) {
                    byte[] value = store.get(keys.get(i));
                    assertThat(value)
                            .as("record %d, entirely before the corrupted byte, must survive", i)
                            .isNotNull();
                    assertThat(value)
                            .as("record %d, entirely before the corrupted byte, must be intact", i)
                            .isEqualTo(values.get(i));
                }
            }
        } finally {
            store.close();
        }
    }
}
