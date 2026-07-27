package kvibe.format;

import java.io.IOException;

/** Thrown when a data file's magic signature is unrecognized or its format version is newer than supported (FR-9). */
public class UnsupportedFormatException extends IOException {

    public UnsupportedFormatException(String message) {
        super(message);
    }
}
