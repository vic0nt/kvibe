package kvibe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KeyTest {

    @Test
    void rejectsNullKey() {
        assertThatThrownBy(() -> Key.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsEmptyKey() {
        assertThatThrownBy(() -> Key.of(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsKeyLongerThan65535Bytes() {
        assertThatThrownBy(() -> Key.of(new byte[65_536])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsBoundaryLengths() {
        assertThat(Key.of(new byte[1]).length()).isEqualTo(1);
        assertThat(Key.of(new byte[65_535]).length()).isEqualTo(65_535);
    }

    @Test
    void copiesInputArrayDefensively() {
        byte[] input = {1, 2, 3};
        Key key = Key.of(input);
        input[0] = 99;
        assertThat(key.toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void toByteArrayReturnsACopyEachTime() {
        Key key = Key.of(new byte[] {1, 2, 3});
        byte[] first = key.toByteArray();
        first[0] = 99;
        assertThat(key.toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void equalsAndHashCodeAreContentBased() {
        Key a = Key.of(new byte[] {1, 2, 3});
        Key b = Key.of(new byte[] {1, 2, 3});
        Key c = Key.of(new byte[] {1, 2, 4});

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
