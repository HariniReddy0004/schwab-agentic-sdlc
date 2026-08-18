package com.schwab.orchestrator.llm;

import com.schwab.orchestrator.framework.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Minimal Anthropic Messages API client using only java.net.http (no SDK jar — unreachable from
 * this build environment). Requires ANTHROPIC_API_KEY in the environment; if it is not set this
 * client fails fast with {@link LlmUnavailableException} rather than attempting a doomed call, so
 * the caller can fall back immediately instead of waiting out a network timeout.
 */
public final class ClaudeClient {
    private static final String DEFAULT_MODEL = "claude-sonnet-4-5-20250929";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public ClaudeClient() {
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
        this.baseUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://api.anthropic.com");
        this.model = System.getenv().getOrDefault("ANTHROPIC_MODEL", DEFAULT_MODEL);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Sends a single-turn message and returns the concatenated text of the response. */
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            throw new LlmUnavailableException("ANTHROPIC_API_KEY is not set; no live LLM call attempted");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new LlmUnavailableException("Anthropic API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return extractText(response.body());
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException("LLM call failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(String responseBody) {
        Map<String, Object> parsed = Json.parseObject(responseBody);
        Object contentObj = parsed.get("content");
        if (!(contentObj instanceof List<?> content) || content.isEmpty()) {
            throw new LlmUnavailableException("Unexpected Anthropic response shape (no content): " + responseBody);
        }
        StringBuilder sb = new StringBuilder();
        for (Object block : content) {
            if (block instanceof Map<?, ?> m && "text".equals(m.get("type"))) {
                sb.append(m.get("text"));
            }
        }
        return sb.toString();
    }
}
