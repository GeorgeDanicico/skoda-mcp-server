package com.gd.skoda.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

/** Response helpers shared by the REST controllers. */
final class ApiResponses {

    private ApiResponses() {
    }

    /**
     * 200 with {@code Cache-Control: no-store}: vehicle position and state are personal data, and the
     * server already caches upstream responses itself.
     */
    static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
