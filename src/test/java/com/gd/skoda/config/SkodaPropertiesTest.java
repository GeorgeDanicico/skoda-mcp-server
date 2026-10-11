package com.gd.skoda.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// ApplicationContextRunner refreshes contexts at run time, without AOT processing; a native image only
// supports AOT-processed contexts. Startup validation is covered natively by the smoke tests.
@DisabledInNativeImage
class SkodaPropertiesTest {

    private static final String VALID_VIN = "TMBJJ7NE8L0123456";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SkodaPropertiesConfiguration.class);

    @Test
    void appliesDefaultsWhenOnlyRequiredValuesAreSet() {
        runner.withPropertyValues("skoda.vin=" + VALID_VIN, "skoda.public-api.key=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SkodaProperties properties = context.getBean(SkodaProperties.class);
                    assertThat(properties.vin()).isEqualTo(VALID_VIN);
                    assertThat(properties.publicApi().key()).isEqualTo("test-key");
                    assertThat(properties.publicApi().baseUrl())
                            .isEqualTo(URI.create("https://public.api.connect.skoda-auto.cz"));
                    assertThat(properties.publicApi().timeout()).isEqualTo(Duration.ofSeconds(15));
                    assertThat(properties.cache().ttl()).isEqualTo(Duration.ofMinutes(5));
                });
    }

    @Test
    void emptyOptionalValuesFallBackToDefaults() {
        // application.yaml maps unset env vars to empty strings, e.g. ${SKODA_CACHE_TTL:}
        runner.withPropertyValues("skoda.vin=" + VALID_VIN, "skoda.public-api.key=test-key",
                        "skoda.public-api.base-url=", "skoda.cache.ttl=")
                .run(context -> {
                    SkodaProperties properties = context.getBean(SkodaProperties.class);
                    assertThat(properties.publicApi().baseUrl())
                            .isEqualTo(URI.create("https://public.api.connect.skoda-auto.cz"));
                    assertThat(properties.cache().ttl()).isEqualTo(Duration.ofMinutes(5));
                });
    }

    @Test
    void bindsExplicitValues() {
        runner.withPropertyValues("skoda.vin=" + VALID_VIN, "skoda.public-api.key=test-key",
                        "skoda.public-api.base-url=http://localhost:9999",
                        "skoda.public-api.timeout=30s", "skoda.cache.ttl=10m")
                .run(context -> {
                    SkodaProperties properties = context.getBean(SkodaProperties.class);
                    assertThat(properties.publicApi().baseUrl()).isEqualTo(URI.create("http://localhost:9999"));
                    assertThat(properties.publicApi().timeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.cache().ttl()).isEqualTo(Duration.ofMinutes(10));
                });
    }

    @Test
    void failsWhenVinIsMissing() {
        runner.withPropertyValues("skoda.public-api.key=test-key")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("skoda.vin")
                        .hasMessageContaining("must be set (env SKODA_VIN)"));
    }

    @Test
    void failsWhenVinIsNotSeventeenCharacters() {
        runner.withPropertyValues("skoda.vin=TMB123", "skoda.public-api.key=test-key")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("skoda.vin")
                        .hasMessageContaining("must be a 17-character VIN"));
    }

    @Test
    void failsWhenApiKeyIsMissing() {
        runner.withPropertyValues("skoda.vin=" + VALID_VIN)
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("publicApi.key")
                        .hasMessageContaining("must be set (env SKODA_API_KEY)"));
    }

    @Test
    void failsWhenDurationsAreNotPositive() {
        runner.withPropertyValues("skoda.vin=" + VALID_VIN, "skoda.public-api.key=test-key",
                        "skoda.public-api.timeout=0s", "skoda.cache.ttl=-1m")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("publicApi.timeout")
                        .hasMessageContaining("cache.ttl"));
    }

    @Test
    void doesNotExposeApiKeyInToString() {
        runner.withPropertyValues("skoda.vin=" + VALID_VIN, "skoda.public-api.key=super-secret")
                .run(context -> assertThat(context.getBean(SkodaProperties.class).toString())
                        .doesNotContain("super-secret"));
    }
}
