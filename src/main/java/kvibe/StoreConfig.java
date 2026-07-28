package kvibe;

import java.util.Objects;

/**
 * Store configuration passed to {@code KvibeStore.open}.
 *
 * @param syncPolicy durability policy (FR-7)
 */
public record StoreConfig(SyncPolicy syncPolicy) {

    /**
     * Validates that {@code syncPolicy} is non-null.
     *
     * @throws NullPointerException if {@code syncPolicy} is {@code null}
     */
    public StoreConfig {
        Objects.requireNonNull(syncPolicy, "syncPolicy");
    }

    /**
     * Returns the safe-by-default configuration: {@link SyncPolicy#EVERY_WRITE} (FR-7).
     *
     * @return a config with {@link SyncPolicy#EVERY_WRITE}
     */
    public static StoreConfig defaults() {
        return new StoreConfig(SyncPolicy.EVERY_WRITE);
    }
}
