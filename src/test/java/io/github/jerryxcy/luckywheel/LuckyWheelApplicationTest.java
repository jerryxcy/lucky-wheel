package io.github.jerryxcy.luckywheel;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LuckyWheelApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void localWheelStartsWithoutSharedInfrastructure() {
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(Flyway.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(EntityManagerFactory.class)).isEmpty();
    }
}
