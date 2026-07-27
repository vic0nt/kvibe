package kvibe.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class FileHeaderTest {

    @Test
    void roundTripsCurrentHeader() throws Exception {
        FileHeader original = FileHeader.current();

        ByteBuffer encoded = original.encode();
        assertThat(encoded.remaining()).isEqualTo(FileHeader.SIZE);

        FileHeader decoded = FileHeader.decode(encoded);
        assertThat(decoded).isEqualTo(original);
        assertThat(encoded.remaining()).isZero();
    }

    @Test
    void rejectsBadMagic() {
        ByteBuffer buf = ByteBuffer.allocate(FileHeader.SIZE);
        buf.put("XXXX".getBytes()).putShort((short) 1).putShort((short) 0).putLong(0L);
        buf.flip();

        assertThatThrownBy(() -> FileHeader.decode(buf)).isInstanceOf(UnsupportedFormatException.class);
    }

    @Test
    void rejectsFormatVersionNewerThanSupported() {
        ByteBuffer buf = ByteBuffer.allocate(FileHeader.SIZE);
        buf.put("KVB1".getBytes())
                .putShort((short) (FileHeader.CURRENT_FORMAT_VERSION + 1))
                .putShort((short) 0)
                .putLong(0L);
        buf.flip();

        assertThatThrownBy(() -> FileHeader.decode(buf)).isInstanceOf(UnsupportedFormatException.class);
    }

    @Test
    void rejectsFormatVersionZero() {
        ByteBuffer buf = ByteBuffer.allocate(FileHeader.SIZE);
        buf.put("KVB1".getBytes()).putShort((short) 0).putShort((short) 0).putLong(0L);
        buf.flip();

        assertThatThrownBy(() -> FileHeader.decode(buf)).isInstanceOf(UnsupportedFormatException.class);
    }

    @Test
    void rejectsTruncatedHeader() {
        ByteBuffer buf = ByteBuffer.allocate(FileHeader.SIZE - 1);
        assertThatThrownBy(() -> FileHeader.decode(buf)).isInstanceOf(UnsupportedFormatException.class);
    }
}
