package kvibe.crash;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Shared key/value encoding between {@link CrashWriterMain} (child JVM) and the crash test (parent). */
final class CrashRecords {

    private CrashRecords() {}

    static byte[] key(int i) {
        return ("k-" + i).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] value(int i) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(i).array();
    }

    static int decodeValue(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }
}
