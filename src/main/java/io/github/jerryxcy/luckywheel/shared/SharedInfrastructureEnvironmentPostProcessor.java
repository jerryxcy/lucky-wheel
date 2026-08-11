package io.github.jerryxcy.luckywheel.shared;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SharedInfrastructureEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final String SHARED_ENABLED = "lucky-wheel.shared.enabled";
    private static final String AUTO_CONFIGURATION_EXCLUDE = "spring.autoconfigure.exclude";
    private static final String PROPERTY_SOURCE_NAME = "sharedInfrastructureDefaults";
    private static final Set<String> SHARED_AUTO_CONFIGURATIONS = Set.of(
            DataSourceAutoConfiguration.class.getName(),
            FlywayAutoConfiguration.class.getName(),
            HibernateJpaAutoConfiguration.class.getName()
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getProperty(SHARED_ENABLED, Boolean.class, false)) {
            return;
        }

        Set<String> exclusions = new LinkedHashSet<>();
        String configuredExclusions = environment.getProperty(AUTO_CONFIGURATION_EXCLUDE, "");
        Arrays.stream(configuredExclusions.split(","))
                .map(String::trim)
                .filter(exclusion -> !exclusion.isEmpty())
                .forEach(exclusions::add);
        exclusions.addAll(SHARED_AUTO_CONFIGURATIONS);

        environment.getPropertySources().addFirst(new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of(AUTO_CONFIGURATION_EXCLUDE, String.join(",", exclusions))
        ));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
