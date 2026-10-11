package com.gd.skoda.upstream;

import static org.assertj.core.api.Assertions.assertThat;

import com.gd.skoda.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ApiKeyExpiryWarningTest {

    private static final Instant NOW = Instant.parse("2026-10-11T08:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final ApiKeyExpiryWarning warning = new ApiKeyExpiryWarning(clock);

    @Test
    void doesNotWarnWhileExpiryIsUnknown() {
        assertThat(warning.warnIfExpiringSoon(null)).isFalse();
    }

    @Test
    void doesNotWarnWhenKeyIsValidForSevenDaysOrMore() {
        assertThat(warning.warnIfExpiringSoon(NOW.plus(Duration.ofDays(7)))).isFalse();
        assertThat(warning.warnIfExpiringSoon(NOW.plus(Duration.ofDays(90)))).isFalse();
    }

    @Test
    void warnsWhenKeyExpiresWithinSevenDays(CapturedOutput output) {
        Instant expiry = NOW.plus(Duration.ofDays(6)).plus(Duration.ofHours(23));

        assertThat(warning.warnIfExpiringSoon(expiry)).isTrue();
        assertThat(output).contains("The MySkoda API key expires at 2026-10-18T07:00:00Z (in 6 days 23 hours)")
                .contains("MySkoda app");
    }

    @Test
    void warnsAtMostOncePerDay(CapturedOutput output) {
        Instant expiry = NOW.plus(Duration.ofDays(3));

        assertThat(warning.warnIfExpiringSoon(expiry)).isTrue();
        clock.advance(Duration.ofHours(1));
        assertThat(warning.warnIfExpiringSoon(expiry)).isFalse();
        clock.advance(Duration.ofHours(22).plus(Duration.ofMinutes(59)));
        assertThat(warning.warnIfExpiringSoon(expiry)).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(warning.warnIfExpiringSoon(expiry)).isTrue();

        assertThat(output.getAll().split("The MySkoda API key expires at", -1)).hasSize(3);
    }

    @Test
    void warnsThatAnExpiredKeyMustBeReplaced(CapturedOutput output) {
        assertThat(warning.warnIfExpiringSoon(NOW.minus(Duration.ofHours(1)))).isTrue();
        assertThat(output).contains("The MySkoda API key expired at 2026-10-11T07:00:00Z")
                .contains("SKODA_API_KEY");
    }
}
