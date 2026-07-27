package kvibe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TR-5: a pool of virtual threads, several writers and several readers, hammering one store for
 * at least 10 seconds. Slow by design (TR-8) — belongs to {@code slowTest}, not {@code test}.
 */
@Tag("slow")
class KvibeStoreConcurrencyTest {

    private static final int WRITERS = 4;
    private static final int READERS = 8;
    private static final Duration RUN_TIME = Duration.ofSeconds(10);

    @TempDir
    Path dir;

    @Test
    void concurrentReadersAndWritersNeverObserveCorruptionOrGoingBackwardsInTime() throws Exception {
        Path path = dir.resolve("store.kvibe");
        try (KvibeStore store = KvibeStore.open(path, StoreConfig.defaults())) {
            List<byte[]> writerKeys = IntStream.range(0, WRITERS)
                    .mapToObj(i -> ("writer-" + i).getBytes(StandardCharsets.UTF_8))
                    .toList();

            AtomicBoolean stop = new AtomicBoolean(false);
            List<Throwable> failures = new CopyOnWriteArrayList<>();

            List<Runnable> tasks = new ArrayList<>();
            for (byte[] key : writerKeys) {
                tasks.add(() -> writerLoop(store, key, stop, failures));
            }
            for (int i = 0; i < READERS; i++) {
                tasks.add(() -> readerLoop(store, writerKeys, stop, failures));
            }

            List<Future<?>> futures;
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                futures = tasks.stream().map(executor::submit).toList();

                Thread.sleep(RUN_TIME.toMillis());
                stop.set(true);

                executor.shutdown();
                assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            }

            for (Future<?> future : futures) {
                future.get(); // rethrows any exception the task escaped with
            }
            assertThat(failures).isEmpty();
        }
    }

    private static void writerLoop(KvibeStore store, byte[] key, AtomicBoolean stop, List<Throwable> failures) {
        try {
            long counter = 0;
            while (!stop.get()) {
                counter++;
                store.put(key, encode(counter));

                byte[] readBack = store.get(key);
                if (readBack == null || decode(readBack) != counter) {
                    throw new AssertionError("read-your-write violated for " + new String(key, StandardCharsets.UTF_8)
                            + ": wrote " + counter + ", read back "
                            + (readBack == null ? "null" : decode(readBack)));
                }
            }
        } catch (Throwable t) {
            failures.add(t);
        }
    }

    private static void readerLoop(KvibeStore store, List<byte[]> keys, AtomicBoolean stop, List<Throwable> failures) {
        try {
            Map<String, Long> lastSeenPerKey = new HashMap<>();
            while (!stop.get()) {
                byte[] key = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
                byte[] value = store.get(key);
                if (value != null) {
                    long counter = decode(value);
                    String keyString = new String(key, StandardCharsets.UTF_8);
                    Long previous = lastSeenPerKey.put(keyString, counter);
                    if (previous != null && counter < previous) {
                        throw new AssertionError(
                                "counter went backwards for " + keyString + ": saw " + previous + " then " + counter);
                    }
                }
            }
        } catch (Throwable t) {
            failures.add(t);
        }
    }

    private static byte[] encode(long counter) {
        return ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
    }

    private static long decode(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }
}
