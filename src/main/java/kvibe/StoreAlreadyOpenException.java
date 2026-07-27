package kvibe;

import java.io.IOException;

/**
 * Thrown when a data file is already locked by another open {@link KvibeStore} — in this process
 * or another — instead of silently corrupting it with two concurrent writers (FR-8).
 */
public class StoreAlreadyOpenException extends IOException {

    public StoreAlreadyOpenException(String message) {
        super(message);
    }

    public StoreAlreadyOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
