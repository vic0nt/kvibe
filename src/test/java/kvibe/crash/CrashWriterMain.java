package kvibe.crash;

import java.nio.file.Path;
import kvibe.KvibeStore;
import kvibe.StoreConfig;
import kvibe.SyncPolicy;

/**
 * Child-JVM half of the TR-3 crash test: opens a store and writes an ever-growing sequence of
 * distinct keys in a loop, printing the index of each confirmed {@code put} to stdout (flushed
 * immediately). The parent process reads that stream to know exactly which writes were
 * confirmed before it kills this process with {@code destroyForcibly()} — see {@link
 * KvibeStoreCrashTest}.
 *
 * <p>Not a JUnit test itself; only ever launched as a subprocess by {@link KvibeStoreCrashTest}.
 */
public final class CrashWriterMain {

    private CrashWriterMain() {}

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]);
        SyncPolicy policy = SyncPolicy.valueOf(args[1]);

        try (KvibeStore store = KvibeStore.open(path, new StoreConfig(policy))) {
            int i = 0;
            while (true) {
                store.put(CrashRecords.key(i), CrashRecords.value(i));
                System.out.println(i);
                System.out.flush();
                i++;
            }
        }
    }
}
