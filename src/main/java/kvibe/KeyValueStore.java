package kvibe;

import java.io.IOException;

/**
 * An embeddable key-value store (FR-1). Implementations follow the concurrency model of
 * REQUIREMENTS.md, section 5: any number of concurrent readers, a single writer serialized
 * internally, and {@code put}-then-{@code get} read-your-writes within the same thread (NFR-3).
 *
 * <p>{@code null} keys or values are rejected with {@link NullPointerException}. Arrays passed to
 * {@code put} are copied on entry; arrays returned by {@code get} belong to the caller (FR-2).
 */
public interface KeyValueStore extends AutoCloseable {

    /**
     * Stores {@code value} under {@code key}, overwriting any previous value.
     *
     * @param key the key, copied on entry; must be non-null and 1-65535 bytes (FR-2)
     * @param value the value, copied on entry; must be non-null and at most 16 MiB (FR-2)
     * @throws IllegalArgumentException if key/value length is out of range (FR-2)
     * @throws IllegalStateException if the store is closed or poisoned (FR-6, NFR-7)
     * @throws IOException if the write fails; the store is poisoned as a side effect (NFR-7)
     */
    void put(byte[] key, byte[] value) throws IOException;

    /**
     * Returns the value stored under {@code key}, or {@code null} if the key does not exist.
     *
     * @param key the key to look up
     * @return the stored value, or {@code null} if absent
     * @throws IllegalStateException if the store is closed (FR-6)
     * @throws IOException if reading the value from disk fails
     */
    byte[] get(byte[] key) throws IOException;

    /**
     * Deletes {@code key}.
     *
     * @param key the key to delete
     * @return {@code true} if the key existed
     * @throws IllegalStateException if the store is closed or poisoned (FR-6, NFR-7)
     * @throws IOException if the write fails; the store is poisoned as a side effect (NFR-7)
     */
    boolean delete(byte[] key) throws IOException;

    /**
     * Returns the number of live keys currently in the store.
     *
     * @return the live key count
     */
    int size();

    /** Idempotent; a second {@code close} is a no-op (FR-6). */
    @Override
    void close() throws IOException;
}
