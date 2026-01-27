package com.tefera.infra.gateway.http;

public class ParseResult {
	public final HttpRequest request;
	public final int bytesConsumed;
	
	public ParseResult(HttpRequest request, int bytesConsumed) {
		this.request = request;
		this.bytesConsumed = bytesConsumed;
	}
}
