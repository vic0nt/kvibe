package kvibe.recovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import kvibe.Key;
import kvibe.Loc;
import kvibe.format.DecodedRecord;
import kvibe.format.InvalidRecordException;
import kvibe.format.RecordCodec;

/**
 * Sequential recovery scan (FR-3, FR-5): replays records from {@code startOffset} to the end of
 * the file, rebuilding the keydir. The first record that fails to decode — whether a torn write
 * or corruption, {@link RecordCodec} does not distinguish the two — ends the scan; both cases are
 * handled identically by stopping and reporting where to truncate.
 */
public final class Recovery {

    private Recovery() {}

    public static RecoveryResult scan(FileChannel channel, long startOffset) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) (channel.size() - startOffset));
        long position = startOffset;
        while (buf.hasRemaining()) {
            int n = channel.read(buf, position);
            if (n < 0) {
                break;
            }
            position += n;
        }
        buf.flip();

        Map<Key, Loc> keydir = new HashMap<>();
        long validTailOffset = startOffset;
        while (buf.hasRemaining()) {
            int recordStart = buf.position();
            DecodedRecord record;
            try {
                record = RecordCodec.decode(buf);
            } catch (InvalidRecordException e) {
                break;
            }

            Key key = Key.of(record.key());
            if (record.tombstone()) {
                keydir.remove(key);
            } else {
                long valueOffset = startOffset + recordStart + RecordCodec.valueOffsetInRecord(key.length());
                keydir.put(key, new Loc(valueOffset, record.value().length));
            }
            validTailOffset = startOffset + buf.position();
        }
        return new RecoveryResult(keydir, validTailOffset);
    }
}
