package com.tefera.infra.gateway.http;

public interface HttpHandler {
	HttpResponse handle(HttpRequest request);
}
