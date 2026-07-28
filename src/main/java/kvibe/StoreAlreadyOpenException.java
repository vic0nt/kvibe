package kvibe;

import java.io.IOException;

/**
 * Thrown when a data file is already locked by another open {@link KvibeStore} — in this process
 * or another — instead of silently corrupting it with two concurrent writers (FR-8).
 */
public class StoreAlreadyOpenException extends IOException {

    /**
     * Creates the exception with a message and no cause.
     *
     * @param message description of which file was already open and how
     */
    public StoreAlreadyOpenException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and an underlying cause.
     *
     * @param message description of which file was already open and how
     * @param cause the underlying exception (e.g. {@link java.nio.channels.OverlappingFileLockException})
     */
    public StoreAlreadyOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
