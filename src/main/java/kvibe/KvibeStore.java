package kvibe;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import kvibe.format.FileHeader;
import kvibe.format.RecordCodec;
import kvibe.recovery.Recovery;
import kvibe.recovery.RecoveryResult;

/**
 * {@link KeyValueStore} on top of a single append-only data file (REQUIREMENTS.md, sections 5-6).
 *
 * <p>This stage does not yet acquire a {@code FileLock} (FR-8) or enter a poisoned state after an
 * unrecoverable write failure (NFR-7) — both are deferred. Concurrent use from multiple threads
 * is not yet safe: the writer-serializing lock (5.1) lands with the concurrency model. Until then,
 * a {@code KvibeStore} must be used from a single thread at a time.
 */
public final class KvibeStore implements KeyValueStore {

    private static final Logger LOG = System.getLogger(KvibeStore.class.getName());

    private final FileChannel channel;
    private final StoreConfig config;
    private final ConcurrentHashMap<Key, Loc> keydir;
    private long tail;
    private boolean closed;

    private KvibeStore(FileChannel channel, StoreConfig config, ConcurrentHashMap<Key, Loc> keydir, long tail) {
        this.channel = channel;
        this.config = config;
        this.keydir = keydir;
        this.tail = tail;
    }

    /**
     * Opens {@code path}, creating it (with a fresh header) if it doesn't exist, or replaying it
     * to rebuild the keydir otherwise (FR-3).
     *
     * @throws kvibe.format.UnsupportedFormatException if the file's magic or format version is
     *     not recognized (FR-9)
     */
    public static KvibeStore open(Path path, StoreConfig config) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(config, "config");

        FileChannel channel =
                FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);

        ConcurrentHashMap<Key, Loc> keydir = new ConcurrentHashMap<>();
        long tail;
        if (channel.size() == 0) {
            ByteBuffer header = FileHeader.current().encode();
            writeFully(channel, header, 0);
            if (config.syncPolicy() == SyncPolicy.EVERY_WRITE) {
                channel.force(false);
            }
            tail = FileHeader.SIZE;
        } else {
            ByteBuffer header = ByteBuffer.allocate(FileHeader.SIZE);
            readFully(channel, header, 0);
            header.flip();
            FileHeader.decode(header);

            RecoveryResult recovered = Recovery.scan(channel, FileHeader.SIZE);
            keydir.putAll(recovered.keydir());
            tail = recovered.validTailOffset();

            long danglingBytes = channel.size() - tail;
            if (danglingBytes > 0) {
                channel.truncate(tail);
                LOG.log(
                        Level.INFO,
                        "truncated {0} dangling byte(s) of a torn/corrupt record in {1}",
                        danglingBytes,
                        path);
            }
        }

        LOG.log(Level.INFO, "opened store at {0}", path);
        return new KvibeStore(channel, config, keydir, tail);
    }

    @Override
    public void put(byte[] key, byte[] value) throws IOException {
        ensureOpen();
        ByteBuffer record = RecordCodec.encode(System.currentTimeMillis(), false, key, value);
        int recordLength = record.remaining();

        long writeAt = tail;
        writeFully(channel, record, writeAt);
        if (config.syncPolicy() == SyncPolicy.EVERY_WRITE) {
            channel.force(false);
        }

        long valueOffset = writeAt + RecordCodec.valueOffsetInRecord(key.length);
        keydir.put(Key.of(key), new Loc(valueOffset, value.length));
        tail = writeAt + recordLength;
    }

    @Override
    public byte[] get(byte[] key) throws IOException {
        ensureOpen();
        Loc loc = keydir.get(Key.of(key));
        if (loc == null) {
            return null;
        }

        ByteBuffer buf = ByteBuffer.allocate(loc.length());
        long position = loc.offset();
        while (buf.hasRemaining()) {
            int n = channel.read(buf, position);
            if (n < 0) {
                throw new IOException("unexpected end of file while reading value at offset " + position);
            }
            position += n;
        }
        return buf.array();
    }

    @Override
    public boolean delete(byte[] key) throws IOException {
        ensureOpen();
        Key k = Key.of(key);
        if (!keydir.containsKey(k)) {
            return false;
        }

        ByteBuffer record = RecordCodec.encode(System.currentTimeMillis(), true, key, new byte[0]);
        int recordLength = record.remaining();

        long writeAt = tail;
        writeFully(channel, record, writeAt);
        if (config.syncPolicy() == SyncPolicy.EVERY_WRITE) {
            channel.force(false);
        }

        keydir.remove(k);
        tail = writeAt + recordLength;
        return true;
    }

    @Override
    public int size() {
        ensureOpen();
        return keydir.size();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        channel.close();
        LOG.log(Level.INFO, "closed store");
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("store is closed");
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buf, long position) throws IOException {
        while (buf.hasRemaining()) {
            position += channel.write(buf, position);
        }
    }

    private static void readFully(FileChannel channel, ByteBuffer buf, long position) throws IOException {
        while (buf.hasRemaining()) {
            int n = channel.read(buf, position);
            if (n < 0) {
                return;
            }
            position += n;
        }
    }
}
