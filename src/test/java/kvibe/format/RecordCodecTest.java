package kvibe.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class RecordCodecTest {

    @Test
    void roundTripsRegularRecord() throws Exception {
        byte[] key = {1, 2, 3};
        byte[] value = {9, 8, 7, 6};

        ByteBuffer encoded = RecordCodec.encode(123_456_789L, false, key, value);
        DecodedRecord decoded = RecordCodec.decode(encoded);

        assertThat(decoded.timestampMillis()).isEqualTo(123_456_789L);
        assertThat(decoded.tombstone()).isFalse();
        assertThat(decoded.key()).containsExactly(key);
        assertThat(decoded.value()).containsExactly(value);
        assertThat(encoded.remaining()).isZero();
    }

    @Test
    void roundTripsTombstone() throws Exception {
        byte[] key = {1, 2, 3};

        ByteBuffer encoded = RecordCodec.encode(1L, true, key, new byte[0]);
        DecodedRecord decoded = RecordCodec.decode(encoded);

        assertThat(decoded.tombstone()).isTrue();
        assertThat(decoded.value()).isEmpty();
    }

    @Test
    void acceptsEmptyValueAsDistinctFromTombstone() throws Exception {
        DecodedRecord decoded = RecordCodec.decode(RecordCodec.encode(1L, false, new byte[] {1}, new byte[0]));

        assertThat(decoded.tombstone()).isFalse();
        assertThat(decoded.value()).isEmpty();
    }

    @Test
    void acceptsBoundaryKeyLengths() throws Exception {
        DecodedRecord min = RecordCodec.decode(RecordCodec.encode(1L, false, new byte[1], new byte[0]));
        assertThat(min.key()).hasSize(1);

        DecodedRecord max =
                RecordCodec.decode(RecordCodec.encode(1L, false, new byte[RecordCodec.MAX_KEY_LENGTH], new byte[0]));
        assertThat(max.key()).hasSize(RecordCodec.MAX_KEY_LENGTH);
    }

    @Test
    void acceptsMaxValueLength() throws Exception {
        byte[] value = new byte[RecordCodec.MAX_VALUE_LENGTH];
        DecodedRecord decoded = RecordCodec.decode(RecordCodec.encode(1L, false, new byte[] {1}, value));
        assertThat(decoded.value()).hasSize(RecordCodec.MAX_VALUE_LENGTH);
    }

    @Test
    void rejectsKeyLongerThanMax() {
        assertThatThrownBy(() -> RecordCodec.encode(1L, false, new byte[RecordCodec.MAX_KEY_LENGTH + 1], new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsValueLongerThanMax() {
        assertThatThrownBy(
                        () -> RecordCodec.encode(1L, false, new byte[] {1}, new byte[RecordCodec.MAX_VALUE_LENGTH + 1]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsCrcMismatch() {
        ByteBuffer encoded = RecordCodec.encode(1L, false, new byte[] {1, 2, 3}, new byte[] {9, 9});
        // flip a bit inside the payload, well past the crc field itself
        int payloadIndex = encoded.limit() - 1;
        encoded.put(payloadIndex, (byte) (encoded.get(payloadIndex) ^ 0xFF));

        assertThatThrownBy(() -> RecordCodec.decode(encoded)).isInstanceOf(InvalidRecordException.class);
    }

    @Test
    void detectsTruncatedFixedPrefix() {
        ByteBuffer buf = ByteBuffer.allocate(5);
        assertThatThrownBy(() -> RecordCodec.decode(buf)).isInstanceOf(InvalidRecordException.class);
    }

    @Test
    void detectsTruncatedKeyValueSection() {
        ByteBuffer encoded = RecordCodec.encode(1L, false, new byte[] {1, 2, 3}, new byte[] {9, 9, 9});
        ByteBuffer torn = encoded.duplicate();
        torn.limit(torn.limit() - 2); // chop off the tail of the value

        assertThatThrownBy(() -> RecordCodec.decode(torn)).isInstanceOf(InvalidRecordException.class);
    }

    @Test
    void rejectsCorruptZeroKeyLengthWithoutTrustingIt() {
        ByteBuffer buf = ByteBuffer.allocate(19);
        buf.putInt(0) // crc, irrelevant: keyLength is checked first
                .putLong(1L)
                .put((byte) 0)
                .putShort((short) 0) // corrupt: keyLength must be >= 1
                .putInt(0);
        buf.flip();

        assertThatThrownBy(() -> RecordCodec.decode(buf)).isInstanceOf(InvalidRecordException.class);
    }

    @Test
    void rejectsCorruptValueLengthWithoutAllocating() {
        ByteBuffer buf = ByteBuffer.allocate(19);
        buf.putInt(0) // crc, irrelevant: valueLength is checked first
                .putLong(1L)
                .put((byte) 0)
                .putShort((short) 1)
                .putInt(-1); // corrupt: unsigned value far above MAX_VALUE_LENGTH
        buf.flip();

        assertThatThrownBy(() -> RecordCodec.decode(buf)).isInstanceOf(InvalidRecordException.class);
    }
}
