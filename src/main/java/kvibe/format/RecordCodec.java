package kvibe.format;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.zip.CRC32C;

/**
 * Encodes/decodes a single record: crc32c(4) + timestampMillis(8) + flags(1) + keyLength(2) +
 * valueLength(4) + key + value (REQUIREMENTS.md, section 6). The CRC covers every field after
 * itself. Byte order is big-endian, {@link ByteBuffer}'s default.
 */
public final class RecordCodec {

    public static final int MAX_KEY_LENGTH = 65_535;
    public static final int MAX_VALUE_LENGTH = 16 * 1024 * 1024;

    private static final int TOMBSTONE_FLAG = 0x1;
    private static final int FIXED_PREFIX_SIZE = 4 + 8 + 1 + 2 + 4;

    private RecordCodec() {}

    /**
     * Offset of the value's first byte relative to the start of a record whose key is {@code
     * keyLength} bytes long. Lets callers (the write path, recovery) compute a value's absolute
     * file offset for {@link kvibe.Loc} without re-parsing the record.
     */
    public static int valueOffsetInRecord(int keyLength) {
        return FIXED_PREFIX_SIZE + keyLength;
    }

    /**
     * Encodes a record into a fresh, flipped buffer ready to be written.
     *
     * @throws IllegalArgumentException if key/value length is out of range (FR-2)
     */
    public static ByteBuffer encode(long timestampMillis, boolean tombstone, byte[] key, byte[] value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (key.length < 1 || key.length > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "key length must be within [1, %d], was %d".formatted(MAX_KEY_LENGTH, key.length));
        }
        if (value.length > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                    "value length must be within [0, %d], was %d".formatted(MAX_VALUE_LENGTH, value.length));
        }

        int totalSize = FIXED_PREFIX_SIZE + key.length + value.length;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.position(4); // crc filled in last, once the rest is known
        buf.putLong(timestampMillis);
        buf.put((byte) (tombstone ? TOMBSTONE_FLAG : 0));
        buf.putShort((short) key.length);
        buf.putInt(value.length);
        buf.put(key);
        buf.put(value);

        buf.putInt(0, (int) crcOf(buf, 4, totalSize));

        buf.flip();
        return buf;
    }

    /**
     * Decodes one record starting at {@code buf}'s current position, advancing it past the
     * record on success.
     *
     * @throws InvalidRecordException if the buffer ends before a complete record, a length field
     *     is out of range, or the CRC32C does not match (FR-5, TR-4)
     */
    public static DecodedRecord decode(ByteBuffer buf) throws InvalidRecordException {
        int start = buf.position();
        if (buf.remaining() < FIXED_PREFIX_SIZE) {
            throw new InvalidRecordException(
                    "incomplete record: need at least %d bytes, have %d".formatted(FIXED_PREFIX_SIZE, buf.remaining()));
        }

        int expectedCrc = buf.getInt();
        long timestampMillis = buf.getLong();
        byte flags = buf.get();
        int keyLength = Short.toUnsignedInt(buf.getShort());
        long valueLength = Integer.toUnsignedLong(buf.getInt());

        if (keyLength < 1 || keyLength > MAX_KEY_LENGTH) {
            throw new InvalidRecordException("corrupt keyLength: " + keyLength);
        }
        if (valueLength > MAX_VALUE_LENGTH) {
            throw new InvalidRecordException("corrupt valueLength: " + valueLength);
        }
        long need = (long) keyLength + valueLength;
        if (buf.remaining() < need) {
            throw new InvalidRecordException(
                    "incomplete record: need %d more bytes for key+value, have %d".formatted(need, buf.remaining()));
        }

        byte[] key = new byte[keyLength];
        buf.get(key);
        byte[] value = new byte[(int) valueLength];
        buf.get(value);

        int recordLength = buf.position() - start;
        int actualCrc = (int) crcOf(buf, start + 4, start + recordLength);
        if (actualCrc != expectedCrc) {
            throw new InvalidRecordException("crc mismatch: expected %d, got %d".formatted(expectedCrc, actualCrc));
        }

        boolean tombstone = (flags & TOMBSTONE_FLAG) != 0;
        return new DecodedRecord(timestampMillis, tombstone, key, value, recordLength);
    }

    private static long crcOf(ByteBuffer buf, int fromInclusive, int toExclusive) {
        ByteBuffer view = buf.duplicate();
        view.position(fromInclusive).limit(toExclusive);
        CRC32C crc = new CRC32C();
        crc.update(view);
        return crc.getValue();
    }
}
