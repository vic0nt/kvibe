package kvibe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StoreConfigTest {

    @Test
    void defaultsToEveryWriteSyncPolicy() {
        assertThat(StoreConfig.defaults().syncPolicy()).isEqualTo(SyncPolicy.EVERY_WRITE);
    }

    @Test
    void rejectsNullSyncPolicy() {
        assertThatThrownBy(() -> new StoreConfig(null)).isInstanceOf(NullPointerException.class);
    }
}
