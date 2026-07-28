package kvibe;

/**
 * Location of a live value's payload within the active data file: {@code offset} points directly
 * at the first byte of the value (not the record header), so a read is a single positional {@code
 * FileChannel.read(buffer, offset)} of exactly {@code length} bytes, with no need to re-parse the
 * record on every {@code get} (REQUIREMENTS.md, 5.1).
 *
 * @param offset absolute byte offset of the value's first byte in the data file
 * @param length length of the value in bytes
 */
public record Loc(long offset, int length) {

    /**
     * Validates that {@code offset} and {@code length} are non-negative.
     *
     * @throws IllegalArgumentException if {@code offset} or {@code length} is negative
     */
    public Loc {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, was " + offset);
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0, was " + length);
        }
    }
}
