package com.gd.skoda.upstream;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;

/**
 * Authenticates every upstream request with the {@code X-API-Key} header and feeds the response
 * headers (key expiry, rate limit, retry-after) into {@link ApiKeyStatus}.
 *
 * <p>The key is never logged; {@link #toString()} masks it.
 */
public final class ApiKeyInterceptor implements ClientHttpRequestInterceptor {

    private final String apiKey;
    private final ApiKeyStatus apiKeyStatus;
    private final ApiKeyExpiryWarning expiryWarning;

    public ApiKeyInterceptor(String apiKey, ApiKeyStatus apiKeyStatus, ApiKeyExpiryWarning expiryWarning) {
        Assert.hasText(apiKey, "apiKey must not be blank");
        this.apiKey = apiKey;
        this.apiKeyStatus = apiKeyStatus;
        this.expiryWarning = expiryWarning;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(UpstreamHeaders.API_KEY, apiKey);
        ClientHttpResponse response = execution.execute(request, body);
        ApiKeyStatus.Snapshot snapshot = apiKeyStatus.recordResponse(response.getHeaders());
        expiryWarning.warnIfExpiringSoon(snapshot.keyExpiresAt());
        return response;
    }

    @Override
    public String toString() {
        return "ApiKeyInterceptor[apiKey=****]";
    }
}
