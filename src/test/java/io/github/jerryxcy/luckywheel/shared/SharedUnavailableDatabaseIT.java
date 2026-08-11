package io.github.jerryxcy.luckywheel.shared;

import io.github.jerryxcy.luckywheel.LuckyWheelApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedUnavailableDatabaseIT {

    @Test
    void sharedInfrastructureRefusesToStartWhenPostgreSqlIsUnavailable() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(LuckyWheelApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--lucky-wheel.shared.enabled=true",
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/lucky_wheel?connectTimeout=1",
                        "--spring.datasource.username=lucky_wheel",
                        "--spring.datasource.password=lucky_wheel",
                        "--spring.datasource.hikari.initialization-fail-timeout=1",
                        "--spring.flyway.connect-retries=0"
                ))
                .isInstanceOf(Exception.class);
    }
}
