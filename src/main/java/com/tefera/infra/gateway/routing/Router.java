package com.tefera.infra.gateway.routing;

import java.util.HashMap;
import java.util.Map;

import com.tefera.infra.gateway.http.HttpHandler;
import com.tefera.infra.gateway.http.HttpRequest;
import com.tefera.infra.gateway.http.HttpResponse;


public class Router {
	private final Map<String, HttpHandler> routes = new HashMap<>();
	
	public void get(String path, HttpHandler handler) {
		routes.put("GET " + path, handler);
	}
	
	public HttpResponse route(HttpRequest request) {
        HttpHandler handler = routes.get(
            request.method + " " + request.path
        );

        if (handler == null) {
            return HttpResponse.notFound();
        }

        return handler.handle(request);
    }
	
}
