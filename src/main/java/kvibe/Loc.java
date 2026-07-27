package kvibe;

/**
 * Location of a live value's payload within the active data file: {@code offset} points directly
 * at the first byte of the value (not the record header), so a read is a single positional {@code
 * FileChannel.read(buffer, offset)} of exactly {@code length} bytes, with no need to re-parse the
 * record on every {@code get} (REQUIREMENTS.md, 5.1).
 */
public record Loc(long offset, int length) {

    public Loc {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0, was " + offset);
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0, was " + length);
        }
    }
}
