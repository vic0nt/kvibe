package kvibe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

/**
 * TR-2: property-based test against a plain {@code HashMap} oracle. {@code reopen} is part of the
 * generated operation mix on purpose — it's the operation that exercises recovery (FR-3).
 */
class KvibeStorePropertyTest {

    private sealed interface Op permits Put, Get, Delete, Reopen {}

    private record Put(byte[] key, byte[] value) implements Op {}

    private record Get(byte[] key) implements Op {}

    private record Delete(byte[] key) implements Op {}

    private record Reopen() implements Op {}

    @Property(tries = 500)
    void behavesLikeAHashMapOracle(@ForAll("ops") List<Op> ops) throws IOException {
        // No @TempDir here (unlike other tests, TR-7): jqwik runs as its own JUnit Platform engine
        // and only resolves @ForAll parameters, so Jupiter's @TempDir extension never fires.
        // jqwik prints its own seed on failure (and supports @Property(seed = "...") to replay it).
        Path dir = Files.createTempDirectory("kvibe-prop");
        Path file = dir.resolve("store.kvibe");
        Map<Key, byte[]> oracle = new HashMap<>();
        KvibeStore store = KvibeStore.open(file, StoreConfig.defaults());
        try {
            for (Op op : ops) {
                if (op instanceof Put p) {
                    store.put(p.key(), p.value());
                    oracle.put(Key.of(p.key()), p.value());
                } else if (op instanceof Get g) {
                    byte[] expected = oracle.get(Key.of(g.key()));
                    byte[] actual = store.get(g.key());
                    if (expected == null) {
                        assertThat(actual).isNull();
                    } else {
                        assertThat(actual).containsExactly(expected);
                    }
                } else if (op instanceof Delete d) {
                    boolean existedInOracle = oracle.remove(Key.of(d.key())) != null;
                    boolean existedInStore = store.delete(d.key());
                    assertThat(existedInStore).isEqualTo(existedInOracle);
                } else if (op instanceof Reopen) {
                    store.close();
                    store = KvibeStore.open(file, StoreConfig.defaults());
                }
            }
            assertThat(store.size()).isEqualTo(oracle.size());
        } finally {
            store.close();
            deleteRecursively(dir);
        }
    }

    @Provide
    Arbitrary<List<Op>> ops() {
        Arbitrary<byte[]> keys = Arbitraries.of("a", "b", "c", "d", "e").map(s -> s.getBytes(StandardCharsets.UTF_8));
        Arbitrary<byte[]> values =
                Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(8);

        Arbitrary<Op> put = Combinators.combine(keys, values).as(Put::new);
        Arbitrary<Op> get = keys.map(Get::new);
        Arbitrary<Op> delete = keys.map(Delete::new);
        Arbitrary<Op> reopen = Arbitraries.just(new Reopen());

        Arbitrary<Op> op = Arbitraries.frequencyOf(
                Tuple.of(40, put), Tuple.of(30, get), Tuple.of(20, delete), Tuple.of(10, reopen));

        return op.list().ofMinSize(1).ofMaxSize(50);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
