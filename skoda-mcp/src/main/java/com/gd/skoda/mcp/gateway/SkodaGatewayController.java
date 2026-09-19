package com.gd.skoda.mcp.gateway;

import com.gd.skoda.mcp.SkodaVehicleFacade;
import com.gd.skoda.mcp.VehicleSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/** Local-only, bearer-protected endpoint used by the Telegram bridge. */
@Profile("gateway")
@RestController
@RequestMapping("/api/v1/car")
public class SkodaGatewayController {
    private final SkodaVehicleFacade facade;
    private final byte[] expectedToken;

    public SkodaGatewayController(SkodaVehicleFacade facade, @Value("${skoda.gateway.token}") String token) {
        this.facade = facade;
        this.expectedToken = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping
    public VehicleSnapshot snapshot(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization);
        return facade.snapshot();
    }

    @PostMapping("/actions/flash")
    public ResponseEntity<Map<String, String>> flash(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization); facade.flash(); return accepted();
    }
    @PostMapping("/actions/honk-and-flash")
    public ResponseEntity<Map<String, String>> honkAndFlash(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization); facade.honkAndFlash(); return accepted();
    }
    @PostMapping("/actions/lock")
    public ResponseEntity<Map<String, String>> lock(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization); facade.lock(); return accepted();
    }
    @PostMapping("/actions/unlock")
    public ResponseEntity<Map<String, String>> unlock(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authorize(authorization); facade.unlock(); return accepted();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> unavailable(IllegalStateException ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "car service unavailable"));
    }

    private ResponseEntity<Map<String, String>> accepted() {
        return ResponseEntity.accepted().body(Map.of("requestId", UUID.randomUUID().toString(), "status", "accepted"));
    }
    private void authorize(String authorization) {
        byte[] supplied = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, supplied)) {
            throw new GatewayUnauthorizedException();
        }
    }
}
