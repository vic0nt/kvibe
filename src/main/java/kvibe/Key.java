package kvibe;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable value-type wrapper over a key's bytes, used as the {@code ConcurrentHashMap} key in
 * the in-memory keydir (REQUIREMENTS.md, 5.1). Provides content-based {@code equals}/{@code
 * hashCode}, which {@code byte[]} lacks.
 */
public final class Key {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 65_535;

    private final byte[] bytes;

    private Key(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Creates a {@code Key} from a defensive copy of {@code key}.
     *
     * @param key the raw key bytes, copied on entry
     * @return a new {@code Key} wrapping a copy of {@code key}
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key.length} is outside {@code [1, 65535]} (FR-2)
     */
    public static Key of(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length < MIN_LENGTH || key.length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "key length must be within [%d, %d], was %d".formatted(MIN_LENGTH, MAX_LENGTH, key.length));
        }
        return new Key(key.clone());
    }

    /**
     * Returns a defensive copy of this key's bytes.
     *
     * @return a copy of the underlying byte array
     */
    public byte[] toByteArray() {
        return bytes.clone();
    }

    /**
     * Returns the number of bytes in this key.
     *
     * @return the key length in bytes
     */
    public int length() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Key other && Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "Key[length=%d]".formatted(bytes.length);
    }
}
