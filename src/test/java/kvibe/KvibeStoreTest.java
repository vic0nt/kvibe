package kvibe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import kvibe.format.FileHeader;
import kvibe.format.UnsupportedFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KvibeStoreTest {

    @TempDir
    Path dir;

    private Path storePath() {
        return dir.resolve("store.kvibe");
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void putThenGetReturnsTheStoredValue() throws IOException {
        try (KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults())) {
            store.put(bytes("k"), bytes("v"));
            assertThat(store.get(bytes("k"))).isEqualTo(bytes("v"));
        }
    }

    @Test
    void getOnMissingKeyReturnsNull() throws IOException {
        try (KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults())) {
            assertThat(store.get(bytes("missing"))).isNull();
        }
    }

    @Test
    void emptyValueIsDistinctFromAbsence() throws IOException {
        try (KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults())) {
            store.put(bytes("k"), new byte[0]);
            assertThat(store.get(bytes("k"))).isNotNull().isEmpty();
        }
    }

    @Test
    void overwritingAKeyKeepsTheLatestValue() throws IOException {
        try (KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults())) {
            store.put(bytes("k"), bytes("first"));
            store.put(bytes("k"), bytes("second"));
            assertThat(store.get(bytes("k"))).isEqualTo(bytes("second"));
            assertThat(store.size()).isEqualTo(1);
        }
    }

    @Test
    void deleteReturnsWhetherTheKeyExisted() throws IOException {
        try (KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults())) {
            assertThat(store.delete(bytes("k"))).isFalse();

            store.put(bytes("k"), bytes("v"));
            assertThat(store.delete(bytes("k"))).isTrue();
            assertThat(store.get(bytes("k"))).isNull();
            assertThat(store.delete(bytes("k"))).isFalse();
        }
    }

    @Test
    void sizeReflectsOnlyLiveKeys() throws IOException {
        try (KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults())) {
            store.put(bytes("a"), bytes("1"));
            store.put(bytes("b"), bytes("2"));
            store.delete(bytes("a"));
            assertThat(store.size()).isEqualTo(1);
        }
    }

    @Test
    void reopenRestoresLiveKeysAndTombstonedDeletesStayDeleted() throws IOException {
        Path path = storePath();
        try (KvibeStore store = KvibeStore.open(path, StoreConfig.defaults())) {
            store.put(bytes("a"), bytes("1"));
            store.put(bytes("b"), bytes("2"));
            store.delete(bytes("b"));
        }

        try (KvibeStore reopened = KvibeStore.open(path, StoreConfig.defaults())) {
            assertThat(reopened.get(bytes("a"))).isEqualTo(bytes("1"));
            assertThat(reopened.get(bytes("b"))).isNull();
            assertThat(reopened.size()).isEqualTo(1);
        }
    }

    @Test
    void closeIsIdempotent() throws IOException {
        KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults());
        store.close();
        store.close();
    }

    @Test
    void operationsAfterCloseThrowIllegalStateException() throws IOException {
        KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults());
        store.close();

        assertThatThrownBy(() -> store.put(bytes("k"), bytes("v"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.get(bytes("k"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.delete(bytes("k"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(store::size).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAFileWithAnUnrecognizedFormatVersion() throws IOException {
        Path path = storePath();
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            ByteBuffer badHeader = ByteBuffer.allocate(FileHeader.SIZE);
            badHeader
                    .put("KVB1".getBytes(StandardCharsets.US_ASCII))
                    .putShort((short) (FileHeader.CURRENT_FORMAT_VERSION + 1))
                    .putShort((short) 0)
                    .putLong(0L);
            badHeader.flip();
            channel.write(badHeader);
        }

        assertThatThrownBy(() -> KvibeStore.open(path, StoreConfig.defaults()))
                .isInstanceOf(UnsupportedFormatException.class);
    }

    @Test
    void truncatesATornTailLeftAfterACrashAndKeepsThePriorRecords() throws IOException {
        Path path = storePath();
        long validSize;
        try (KvibeStore store = KvibeStore.open(path, StoreConfig.defaults())) {
            store.put(bytes("a"), bytes("1"));
            validSize = java.nio.file.Files.size(path);
        }

        // Simulate a crash mid-write: append a few garbage bytes past the last valid record.
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            ByteBuffer garbage = ByteBuffer.wrap(new byte[] {1, 2, 3});
            channel.write(garbage, validSize);
        }
        assertThat(java.nio.file.Files.size(path)).isEqualTo(validSize + 3);

        try (KvibeStore reopened = KvibeStore.open(path, StoreConfig.defaults())) {
            assertThat(reopened.get(bytes("a"))).isEqualTo(bytes("1"));
            assertThat(reopened.size()).isEqualTo(1);
        }
        assertThat(java.nio.file.Files.size(path)).isEqualTo(validSize);
    }

    @Test
    void openingAnAlreadyOpenFileFailsClearlyAndTheLockIsReleasedOnClose() throws IOException {
        Path path = storePath();
        try (KvibeStore first = KvibeStore.open(path, StoreConfig.defaults())) {
            assertThatThrownBy(() -> KvibeStore.open(path, StoreConfig.defaults()))
                    .isInstanceOf(StoreAlreadyOpenException.class);
        }

        // first's lock was released by close(); a second open() now succeeds.
        try (KvibeStore reopened = KvibeStore.open(path, StoreConfig.defaults())) {
            assertThat(reopened.size()).isZero();
        }
    }

    @Test
    void neverSyncPolicyStillAllowsReadingBackAPutValue() throws IOException {
        try (KvibeStore store = KvibeStore.open(storePath(), new StoreConfig(SyncPolicy.NEVER))) {
            store.put(bytes("k"), bytes("v"));
            assertThat(store.get(bytes("k"))).isEqualTo(bytes("v"));
        }
    }

    @Test
    void aWriteFailurePoisonsTheStoreAndRejectsFurtherMutations() throws Exception {
        KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults());
        store.put(bytes("a"), bytes("1"));

        // Simulate an unrecoverable write failure (e.g. a disk error) by breaking the channel
        // out from under the store, bypassing KvibeStore.close() so its own state is untouched.
        Field channelField = KvibeStore.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        ((FileChannel) channelField.get(store)).close();

        assertThatThrownBy(() -> store.put(bytes("b"), bytes("2"))).isInstanceOf(IOException.class);

        assertThatThrownBy(() -> store.put(bytes("c"), bytes("3"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.delete(bytes("a"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void poisonedStoreStillAllowsReadsOfPreviouslyCommittedData() throws Exception {
        try (KvibeStore store = KvibeStore.open(storePath(), StoreConfig.defaults())) {
            store.put(bytes("a"), bytes("1"));

            // Flip the poisoned flag directly, without touching the channel, to isolate the
            // "reads still work" claim from whether the underlying I/O itself still succeeds.
            Field poisonedField = KvibeStore.class.getDeclaredField("poisoned");
            poisonedField.setAccessible(true);
            poisonedField.set(store, true);

            assertThat(store.get(bytes("a"))).isEqualTo(bytes("1"));
            assertThat(store.size()).isEqualTo(1);
            assertThatThrownBy(() -> store.put(bytes("b"), bytes("2"))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> store.delete(bytes("a"))).isInstanceOf(IllegalStateException.class);
        }
    }
}
