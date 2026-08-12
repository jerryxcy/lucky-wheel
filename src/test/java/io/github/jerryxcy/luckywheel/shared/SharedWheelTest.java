package io.github.jerryxcy.luckywheel.shared;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedWheelTest {

    @Test
    void wheelNameIsMutableAndNormalizedByTheAggregate() {
        SharedWheel wheel = SharedWheel.create(
                new CreateSharedWheelRequest("Original", false, List.of())
        );

        wheel.rename("  Renamed wheel  ");

        assertThat(wheel.name()).isEqualTo("Renamed wheel");
    }

    @Test
    void renamedWheelStillEnforcesTheNameInvariant() {
        SharedWheel wheel = SharedWheel.create(
                new CreateSharedWheelRequest("Original", false, List.of())
        );

        assertThatThrownBy(() -> wheel.rename("   "))
                .isInstanceOf(SharedWheelValidationException.class);
        assertThat(wheel.name()).isEqualTo("Original");
    }
}
