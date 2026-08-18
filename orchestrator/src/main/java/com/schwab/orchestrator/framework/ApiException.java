package com.schwab.orchestrator.framework;

/** Exception carrying an HTTP status code + machine-readable error code, caught centrally by the Router. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "CONFLICT", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }

    public static ApiException gone(String message) {
        return new ApiException(410, "GONE", message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, "RATE_LIMITED", message);
    }

    public static ApiException payloadTooLarge(String message) {
        return new ApiException(413, "PAYLOAD_TOO_LARGE", message);
    }
}
