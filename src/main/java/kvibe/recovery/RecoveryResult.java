package kvibe.recovery;

import java.util.Map;
import kvibe.Key;
import kvibe.Loc;

/**
 * Result of a sequential recovery scan (FR-3): the rebuilt keydir, and the file offset up to
 * which records were valid — everything from {@code validTailOffset} to the file's actual size
 * is a torn or corrupt tail that the caller must truncate away (FR-5).
 */
public record RecoveryResult(Map<Key, Loc> keydir, long validTailOffset) {}
