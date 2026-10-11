package com.gd.skoda.upstream;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs a warning when the API key is about to expire (or has expired), at most once per
 * {@link #REPEAT_INTERVAL} so that frequent upstream calls do not flood the log.
 *
 * <p>In the {@code mcp} profile the warning goes to STDERR (see {@code logback-spring.xml}).
 */
public final class ApiKeyExpiryWarning {

    /** Warn when the key expires within this period. */
    public static final Duration WARNING_PERIOD = Duration.ofDays(7);
    /** Minimum time between two warnings. */
    public static final Duration REPEAT_INTERVAL = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(ApiKeyExpiryWarning.class);

    private final Clock clock;
    private final AtomicReference<@Nullable Instant> lastWarningAt = new AtomicReference<>();

    public ApiKeyExpiryWarning(Clock clock) {
        this.clock = clock;
    }

    /**
     * Logs a warning if {@code keyExpiresAt} is less than {@link #WARNING_PERIOD} away and no warning
     * was logged within the last {@link #REPEAT_INTERVAL}.
     *
     * @param keyExpiresAt the key's expiry, {@code null} if unknown
     * @return whether a warning was logged
     */
    public boolean warnIfExpiringSoon(@Nullable Instant keyExpiresAt) {
        if (keyExpiresAt == null) {
            return false;
        }
        Instant now = clock.instant();
        Duration validFor = Duration.between(now, keyExpiresAt);
        if (validFor.compareTo(WARNING_PERIOD) >= 0) {
            return false;
        }
        Instant previous = lastWarningAt.get();
        if (previous != null && now.isBefore(previous.plus(REPEAT_INTERVAL))) {
            return false;
        }
        if (!lastWarningAt.compareAndSet(previous, now)) {
            return false; // another thread just logged it
        }
        if (validFor.isNegative() || validFor.isZero()) {
            log.warn("The MySkoda API key expired at {}. Create a new API key in the MySkoda app "
                    + "and set it as SKODA_API_KEY.", keyExpiresAt);
        } else {
            log.warn("The MySkoda API key expires at {} (in {} days {} hours). Create a new API key in the "
                    + "MySkoda app and update SKODA_API_KEY before then.",
                    keyExpiresAt, validFor.toDays(), validFor.toHoursPart());
        }
        return true;
    }
}
