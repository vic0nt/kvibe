package kvibe.format;

import java.io.IOException;

/**
 * Thrown when a record cannot be decoded: the buffer ends before a complete record, a length
 * field is out of range (guards against trusting a corrupted length, FR-5/TR-4), or the CRC32C
 * does not match. Recovery treats all of these the same way: stop and truncate at this point.
 */
public class InvalidRecordException extends IOException {

    public InvalidRecordException(String message) {
        super(message);
    }
}
