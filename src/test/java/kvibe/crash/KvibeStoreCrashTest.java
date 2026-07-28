package kvibe.crash;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kvibe.KvibeStore;
import kvibe.StoreAlreadyOpenException;
import kvibe.StoreConfig;
import kvibe.SyncPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TR-3: a child JVM writes an ever-growing sequence of keys in a loop ({@link CrashWriterMain});
 * the parent kills it with {@code destroyForcibly()} at a random moment and checks that every
 * write the child confirmed (printed to stdout before dying) survives the crash intact.
 *
 * <p>Iteration count defaults to 20 (CI budget); the {@code crashTestExtended} Gradle task
 * overrides it to 500+ for local runs (Definition of Done, section 10).
 */
@Tag("slow")
class KvibeStoreCrashTest {

    private static final int ITERATIONS = Integer.getInteger("kvibe.crashTest.iterations", 20);

    @TempDir
    Path dir;

    @Test
    void childProcessKilledMidWriteNeverLosesOrCorruptsConfirmedRecords() throws Exception {
        long seed = new Random().nextLong();
        Random rnd = new Random(seed);
        SyncPolicy[] policies = SyncPolicy.values();

        for (int iter = 0; iter < ITERATIONS; iter++) {
            SyncPolicy policy = policies[rnd.nextInt(policies.length)];
            long killDelayMillis = 20 + rnd.nextInt(300);
            try {
                runOneCrash(iter, policy, killDelayMillis);
            } catch (AssertionError | Exception e) {
                throw new AssertionError(
                        "crash test failed on iteration %d (policy=%s, killDelayMillis=%d, seed=%d)"
                                .formatted(iter, policy, killDelayMillis, seed),
                        e);
            }
        }
    }

    private void runOneCrash(int iter, SyncPolicy policy, long killDelayMillis) throws Exception {
        Path path = dir.resolve("crash-" + iter + ".kvibe");
        String javaBin = ProcessHandle.current().info().command().orElseThrow();
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin, "-cp", classpath, CrashWriterMain.class.getName(), path.toString(), policy.name());
        Process child = pb.start();

        AtomicInteger lastConfirmed = new AtomicInteger(-1);
        StringBuilder stderr = new StringBuilder();
        Thread stdoutReader =
                new Thread(() -> drain(child.inputReader(), line -> lastConfirmed.set(Integer.parseInt(line))));
        Thread stderrReader = new Thread(
                () -> drain(child.errorReader(), line -> stderr.append(line).append('\n')));
        stdoutReader.setDaemon(true);
        stderrReader.setDaemon(true);
        stdoutReader.start();
        stderrReader.start();

        Thread.sleep(killDelayMillis);
        child.destroyForcibly();
        assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
        stdoutReader.join(5_000);
        stderrReader.join(5_000);

        int confirmed = lastConfirmed.get();

        KvibeStore store;
        try {
            store = openWithRetry(path);
        } catch (IOException e) {
            if (confirmed == -1) {
                // Child was killed before it confirmed a single write (e.g. still starting up) —
                // nothing to verify, and an unwritten/torn header on a file with zero confirmed
                // records is not the guarantee TR-3 is about.
                return;
            }
            throw new AssertionError(
                    "store failed to reopen after crash, confirmed=" + confirmed + ", stderr=" + stderr, e);
        }

        try {
            for (int i = 0; i <= confirmed; i++) {
                byte[] value = store.get(CrashRecords.key(i));
                assertThat(value).as("record %d missing after crash", i).isNotNull();
                assertThat(CrashRecords.decodeValue(value))
                        .as("record %d corrupted after crash", i)
                        .isEqualTo(i);
            }
        } finally {
            store.close();
        }
    }

    private KvibeStore openWithRetry(Path path) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                return KvibeStore.open(path, StoreConfig.defaults());
            } catch (StoreAlreadyOpenException e) {
                // The OS releases the child's FileLock when it dies, but that can lag a hair
                // behind Process.waitFor() returning — retry briefly instead of flaking.
                Thread.sleep(50);
            }
        }
        return KvibeStore.open(path, StoreConfig.defaults());
    }

    private static void drain(BufferedReader reader, java.util.function.Consumer<String> onLine) {
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                onLine.accept(line);
            }
        } catch (IOException | NumberFormatException ignored) {
            // Pipe closed mid-line, or a torn final line right as the process died — the last
            // partial update is simply not counted as confirmed, which is the safe direction.
        }
    }
}
