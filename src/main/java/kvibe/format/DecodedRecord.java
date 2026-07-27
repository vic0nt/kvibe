package kvibe.format;

/**
 * A single record decoded from the data file. {@code recordLength} is the total number of bytes
 * the record occupied, needed by recovery to advance to the next record.
 */
public record DecodedRecord(long timestampMillis, boolean tombstone, byte[] key, byte[] value, int recordLength) {}
