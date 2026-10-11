package com.gd.skoda.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration shared by every run mode, bound from {@code skoda.*} and validated at startup.
 *
 * @param vin       VIN of the vehicle the configured API key is bound to (env {@code SKODA_VIN})
 * @param publicApi MyŠkoda Public API connection settings
 * @param cache     vehicle snapshot cache settings
 */
@Validated
@ConfigurationProperties("skoda")
public record SkodaProperties(
        @NotBlank(message = "must be set (env SKODA_VIN)")
        // empty is left to @NotBlank so a missing VIN reports a single error
        @Pattern(regexp = "^([A-HJ-NPR-Z0-9]{17})?$",
                message = "must be a 17-character VIN (upper-case letters and digits, no I, O or Q)")
        String vin,
        @Valid @DefaultValue PublicApi publicApi,
        @Valid @DefaultValue Cache cache) {

    /**
     * @param key     MyŠkoda Public API key, sent as {@code X-API-Key} (env {@code SKODA_API_KEY})
     * @param baseUrl Public API base URL (env {@code SKODA_PUBLIC_API_URL})
     * @param timeout connect/read timeout for upstream calls
     */
    public record PublicApi(
            @NotBlank(message = "must be set (env SKODA_API_KEY); create a key in the MySkoda app")
            String key,
            @NotNull @DefaultValue("https://public.api.connect.skoda-auto.cz")
            URI baseUrl,
            @NotNull @DurationMin(millis = 1, message = "must be positive") @DefaultValue("15s")
            Duration timeout) {

        @Override
        public String toString() {
            return "PublicApi[key=" + Secrets.mask(key) + ", baseUrl=" + baseUrl + ", timeout=" + timeout + "]";
        }
    }

    /**
     * @param ttl how long a vehicle snapshot is served before the upstream API is called again
     *            (env {@code SKODA_CACHE_TTL})
     */
    public record Cache(
            @NotNull @DurationMin(millis = 1, message = "must be positive") @DefaultValue("5m")
            Duration ttl) {
    }
}
