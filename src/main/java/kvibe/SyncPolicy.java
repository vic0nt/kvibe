package kvibe;

import java.nio.channels.FileChannel;

/** Write durability policy (FR-7). */
public enum SyncPolicy {

    /** Only {@code write}; flush to disk is left to the OS. Fast, loses the tail on power loss. */
    NEVER,

    /**
     * Calls {@link FileChannel#force(boolean) force(false)} after every write. Slow, but once
     * {@code put} returns, the data is on disk.
     */
    EVERY_WRITE
}
