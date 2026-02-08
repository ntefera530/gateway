package com.tefera.infra.gateway.http;

public final class ParseResult {

    public enum Status {
        INCOMPLETE,
        COMPLETE,
        ERROR
    }

    private final Status status;
    private final HttpRequest request;   // only set if COMPLETE
    private final int bytesConsumed;

    private ParseResult(Status status, HttpRequest request, int bytesConsumed) {
        this.status = status;
        this.request = request;
        this.bytesConsumed = bytesConsumed;
    }

    // -------- Factory methods --------

    public static ParseResult incomplete() {
        return new ParseResult(Status.INCOMPLETE, null, 0);
    }

    public static ParseResult complete(HttpRequest request, int bytesConsumed) {
        return new ParseResult(Status.COMPLETE, request, bytesConsumed);
    }

    public static ParseResult error() {
        return new ParseResult(Status.ERROR, null, 0);
    }

    // -------- Helpers --------

    public boolean isDone() {
        return status == Status.COMPLETE;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public int getBytesConsumed() {
        return bytesConsumed;
    }
}