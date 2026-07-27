package kvibe.format;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The 16-byte header written once at file creation (REQUIREMENTS.md, section 6): 4-byte ASCII
 * magic, uint16 format version, uint16 reserved flags, 8 reserved bytes.
 */
public record FileHeader(int formatVersion) {

    public static final int SIZE = 16;
    public static final int CURRENT_FORMAT_VERSION = 1;

    private static final byte[] MAGIC = "KVB1".getBytes(StandardCharsets.US_ASCII);

    public FileHeader {
        if (formatVersion < 1 || formatVersion > 0xFFFF) {
            throw new IllegalArgumentException("formatVersion must be within [1, 65535], was " + formatVersion);
        }
    }

    /** Header for a newly created file, at the current format version. */
    public static FileHeader current() {
        return new FileHeader(CURRENT_FORMAT_VERSION);
    }

    /** Encodes this header into a fresh, flipped, {@value #SIZE}-byte buffer ready to be written. */
    public ByteBuffer encode() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE);
        buf.put(MAGIC);
        buf.putShort((short) formatVersion);
        buf.putShort((short) 0); // flags, reserved
        buf.putLong(0L); // reserved
        buf.flip();
        return buf;
    }

    /**
     * Decodes a header from {@code buf}, advancing its position by {@value #SIZE}.
     *
     * @throws UnsupportedFormatException if the buffer is truncated, the magic doesn't match, or
     *     the format version is newer than {@link #CURRENT_FORMAT_VERSION} (FR-9)
     */
    public static FileHeader decode(ByteBuffer buf) throws UnsupportedFormatException {
        if (buf.remaining() < SIZE) {
            throw new UnsupportedFormatException(
                    "truncated file header: need %d bytes, have %d".formatted(SIZE, buf.remaining()));
        }
        byte[] magic = new byte[MAGIC.length];
        buf.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new UnsupportedFormatException(
                    "unrecognized magic: expected " + new String(MAGIC, StandardCharsets.US_ASCII));
        }
        int formatVersion = Short.toUnsignedInt(buf.getShort());
        buf.getShort(); // flags, reserved
        buf.getLong(); // reserved
        if (formatVersion < 1 || formatVersion > CURRENT_FORMAT_VERSION) {
            throw new UnsupportedFormatException("unsupported format version: " + formatVersion);
        }
        return new FileHeader(formatVersion);
    }
}
