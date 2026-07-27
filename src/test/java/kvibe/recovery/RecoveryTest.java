package kvibe.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import kvibe.Key;
import kvibe.Loc;
import kvibe.format.RecordCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryTest {

    @TempDir
    Path dir;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private FileChannel open() throws IOException {
        Path path = dir.resolve("data");
        return FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    private static void append(FileChannel channel, ByteBuffer record) throws IOException {
        long position = channel.size();
        while (record.hasRemaining()) {
            position += channel.write(record, position);
        }
    }

    @Test
    void scanOfAnEmptyRegionYieldsAnEmptyKeydirAtStartOffset() throws IOException {
        try (FileChannel channel = open()) {
            RecoveryResult result = Recovery.scan(channel, 0);
            assertThat(result.keydir()).isEmpty();
            assertThat(result.validTailOffset()).isZero();
        }
    }

    @Test
    void scanRebuildsTheKeydirAndAppliesTombstonesInOrder() throws IOException {
        try (FileChannel channel = open()) {
            append(channel, RecordCodec.encode(1L, false, bytes("a"), bytes("1")));
            append(channel, RecordCodec.encode(2L, false, bytes("b"), bytes("2")));
            append(channel, RecordCodec.encode(3L, true, bytes("a"), new byte[0]));

            RecoveryResult result = Recovery.scan(channel, 0);

            assertThat(result.keydir()).doesNotContainKey(Key.of(bytes("a")));
            Loc bLoc = result.keydir().get(Key.of(bytes("b")));
            assertThat(bLoc).isNotNull();
            assertThat(result.validTailOffset()).isEqualTo(channel.size());

            ByteBuffer value = ByteBuffer.allocate(bLoc.length());
            channel.read(value, bLoc.offset());
            assertThat(value.array()).isEqualTo(bytes("2"));
        }
    }

    @Test
    void scanStopsAtTheFirstInvalidRecordAndReportsWhereToTruncate() throws IOException {
        try (FileChannel channel = open()) {
            append(channel, RecordCodec.encode(1L, false, bytes("a"), bytes("1")));
            long validEnd = channel.size();
            append(channel, ByteBuffer.wrap(new byte[] {9, 9, 9})); // torn/garbage tail

            RecoveryResult result = Recovery.scan(channel, 0);

            assertThat(result.validTailOffset()).isEqualTo(validEnd);
            assertThat(result.keydir()).containsKey(Key.of(bytes("a")));
        }
    }

    @Test
    void scanStartsFromTheGivenOffsetSkippingTheFileHeader() throws IOException {
        try (FileChannel channel = open()) {
            long headerSize = 16;
            append(channel, ByteBuffer.allocate((int) headerSize)); // stand-in header
            append(channel, RecordCodec.encode(1L, false, bytes("a"), bytes("1")));

            RecoveryResult result = Recovery.scan(channel, headerSize);

            assertThat(result.keydir()).containsKey(Key.of(bytes("a")));
            assertThat(result.validTailOffset()).isEqualTo(channel.size());
        }
    }
}
