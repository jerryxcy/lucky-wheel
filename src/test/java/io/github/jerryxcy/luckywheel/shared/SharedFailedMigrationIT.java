package io.github.jerryxcy.luckywheel.shared;

import io.github.jerryxcy.luckywheel.LuckyWheelApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class SharedFailedMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void sharedInfrastructureRefusesToStartWhenMigrationFails() {
        assertThatThrownBy(this::startWithBrokenMigration)
                .isInstanceOf(Exception.class);
    }

    private void startWithBrokenMigration() {
        try (var ignored = new SpringApplicationBuilder(LuckyWheelApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--lucky-wheel.shared.enabled=true",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.flyway.locations=classpath:db/broken"
                )) {
            // A valid startup is the failure case for this test.
        }
    }
}
