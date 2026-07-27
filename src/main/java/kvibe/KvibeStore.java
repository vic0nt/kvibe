package kvibe;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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
 * <p>This stage does not yet serialize concurrent writers with a {@code ReentrantLock} (5.1) — the
 * writer-serializing lock lands with the concurrency model. Until then, a {@code KvibeStore} must
 * be used from a single thread at a time.
 */
public final class KvibeStore implements KeyValueStore {

    private static final Logger LOG = System.getLogger(KvibeStore.class.getName());

    private final FileChannel channel;
    private final FileLock lock;
    private final StoreConfig config;
    private final ConcurrentHashMap<Key, Loc> keydir;
    private long tail;
    private boolean closed;
    private boolean poisoned;

    private KvibeStore(
            FileChannel channel, FileLock lock, StoreConfig config, ConcurrentHashMap<Key, Loc> keydir, long tail) {
        this.channel = channel;
        this.lock = lock;
        this.config = config;
        this.keydir = keydir;
        this.tail = tail;
    }

    /**
     * Opens {@code path}, creating it (with a fresh header) if it doesn't exist, or replaying it
     * to rebuild the keydir otherwise (FR-3).
     *
     * @throws StoreAlreadyOpenException if the file is already locked by another open {@code
     *     KvibeStore}, in this process or another (FR-8)
     * @throws kvibe.format.UnsupportedFormatException if the file's magic or format version is
     *     not recognized (FR-9)
     */
    public static KvibeStore open(Path path, StoreConfig config) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(config, "config");

        FileChannel channel =
                FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock lock = lockOrClose(channel, path);

        try {
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
            return new KvibeStore(channel, lock, config, keydir, tail);
        } catch (IOException e) {
            releaseAndCloseSuppressingErrorsInto(e, lock, channel);
            throw e;
        }
    }

    private static FileLock lockOrClose(FileChannel channel, Path path) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new StoreAlreadyOpenException("data file is locked by another process: " + path);
            }
            return lock;
        } catch (OverlappingFileLockException e) {
            channel.close();
            throw new StoreAlreadyOpenException("data file is already open in this process: " + path, e);
        }
    }

    private static void releaseAndCloseSuppressingErrorsInto(IOException target, FileLock lock, FileChannel channel) {
        try {
            lock.release();
        } catch (IOException suppressed) {
            target.addSuppressed(suppressed);
        }
        try {
            channel.close();
        } catch (IOException suppressed) {
            target.addSuppressed(suppressed);
        }
    }

    @Override
    public void put(byte[] key, byte[] value) throws IOException {
        ensureWritable();
        ByteBuffer record = RecordCodec.encode(System.currentTimeMillis(), false, key, value);
        int recordLength = record.remaining();

        long writeAt = tail;
        try {
            writeFully(channel, record, writeAt);
            if (config.syncPolicy() == SyncPolicy.EVERY_WRITE) {
                channel.force(false);
            }
        } catch (IOException e) {
            poisoned = true;
            throw e;
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
        ensureWritable();
        Key k = Key.of(key);
        if (!keydir.containsKey(k)) {
            return false;
        }

        ByteBuffer record = RecordCodec.encode(System.currentTimeMillis(), true, key, new byte[0]);
        int recordLength = record.remaining();

        long writeAt = tail;
        try {
            writeFully(channel, record, writeAt);
            if (config.syncPolicy() == SyncPolicy.EVERY_WRITE) {
                channel.force(false);
            }
        } catch (IOException e) {
            poisoned = true;
            throw e;
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
        try {
            lock.release();
        } finally {
            channel.close();
        }
        LOG.log(Level.INFO, "closed store");
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("store is closed");
        }
    }

    /**
     * Same as {@link #ensureOpen()}, plus rejects mutating calls after a write failure has left
     * the store {@link #poisoned} (NFR-7). Reads are unaffected: the keydir only ever references
     * writes that fully completed (5.3), so it stays consistent even after a later write fails.
     */
    private void ensureWritable() {
        ensureOpen();
        if (poisoned) {
            throw new IllegalStateException(
                    "store is poisoned after a write failure; close and reopen to recover (FR-3)");
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
