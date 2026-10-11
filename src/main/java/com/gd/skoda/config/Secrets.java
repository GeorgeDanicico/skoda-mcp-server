package com.gd.skoda.config;

/** Keeps secrets out of {@code toString()} output and therefore out of logs. */
final class Secrets {

    private Secrets() {
    }

    static String mask(String secret) {
        return (secret == null || secret.isEmpty()) ? "<unset>" : "****";
    }
}
