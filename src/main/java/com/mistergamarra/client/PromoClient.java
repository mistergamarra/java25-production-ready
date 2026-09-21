package com.mistergamarra.client;

import module java.base;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.mistergamarra.config.Logger;

public final class PromoClient {

    // FIX: Force the HttpClient to route all internal networking worker tasks
    // onto Loom Virtual Threads instead of heavy platform operating system threads.
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(300))
            .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 100;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private static String getEnvOrProperty(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : System.getProperty(key, defaultValue);
    }

    private static final String MOCK_API_URL = getEnvOrProperty(
            "PROMO_API_URL",
            "http://promo-mock-server:8081/v1/promos/validate?code="
    );

    public static boolean validatePromoCode(String promoCode) {
        if (promoCode == null || promoCode.isBlank()) {
            return false;
        }

        long backoffDelay = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(MOCK_API_URL + java.net.URLEncoder.encode(promoCode, java.nio.charset.StandardCharsets.UTF_8)))
                        .timeout(java.time.Duration.ofMillis(300))
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 500) {
                    Logger.warn("Promo API error response: " + response.statusCode() + " on attempt " + attempt + "/" + MAX_ATTEMPTS, "HTTP-Client");
                    if (attempt < MAX_ATTEMPTS) {
                        executeBackoffSleep(backoffDelay, attempt, promoCode);
                        backoffDelay = (long) (backoffDelay * BACKOFF_MULTIPLIER);
                        continue;
                    }
                    return false;
                }

                return response.statusCode() == 200;

            } catch (Exception e) {
                Logger.warn("Network anomaly encountered: " + e.getMessage() + " on attempt " + attempt + "/" + MAX_ATTEMPTS, "HTTP-Client");

                if (attempt < MAX_ATTEMPTS) {
                    executeBackoffSleep(backoffDelay, attempt, promoCode);
                    backoffDelay = (long) (backoffDelay * BACKOFF_MULTIPLIER);
                } else {
                    Logger.error("Promo validation permanently failed after " + MAX_ATTEMPTS + " resilient sweeps.", "HTTP-Client", e);
                    return false;
                }
            }
        }
        return false;
    }

    private static void executeBackoffSleep(long delayMs, int currentAttempt, String promoCode) {
        try {
            Logger.info("Backing off execution loop for code: " + promoCode + ". Waiting " + delayMs + "ms before retry attempt " + (currentAttempt + 1), "HTTP-Client");
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Resilient execution backoff interrupted", ie);
        }
    }
}
