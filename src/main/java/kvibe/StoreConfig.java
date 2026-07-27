package kvibe;

import java.util.Objects;

/** Store configuration passed to {@code KvibeStore.open}. */
public record StoreConfig(SyncPolicy syncPolicy) {

    public StoreConfig {
        Objects.requireNonNull(syncPolicy, "syncPolicy");
    }

    /** Safe-by-default configuration: {@link SyncPolicy#EVERY_WRITE} (FR-7). */
    public static StoreConfig defaults() {
        return new StoreConfig(SyncPolicy.EVERY_WRITE);
    }
}
