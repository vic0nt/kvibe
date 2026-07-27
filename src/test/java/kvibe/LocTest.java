package kvibe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocTest {

    @Test
    void rejectsNegativeOffset() {
        assertThatThrownBy(() -> new Loc(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeLength() {
        assertThatThrownBy(() -> new Loc(0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsZeroLengthForEmptyValues() {
        Loc loc = new Loc(0, 0);
        assertThat(loc.offset()).isZero();
        assertThat(loc.length()).isZero();
    }
}
