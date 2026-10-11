package com.gd.skoda.support;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Native-image hints for the test suite ({@code -PnativeTest}), registered through
 * {@code src/test/resources/META-INF/spring/aot.factories} so that Spring's test AOT processing applies
 * them to the native test executable.
 */
public final class TestNativeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // Upstream JSON fixtures, loaded with Class#getResourceAsStream by the non-Spring unit tests.
        hints.resources().registerPattern("upstream/*.json");
    }
}
