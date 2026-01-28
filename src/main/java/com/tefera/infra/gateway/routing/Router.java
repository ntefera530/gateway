package com.tefera.infra.gateway.routing;

import java.util.HashMap;
import java.util.Map;

import com.tefera.infra.gateway.http.AsyncHttpHandler;
import com.tefera.infra.gateway.http.HttpHandler;
import com.tefera.infra.gateway.http.HttpRequest;
import com.tefera.infra.gateway.http.HttpResponse;


public class Router {
	//private final Map<String, HttpHandler> routes = new HashMap<>();
	private final Map<String, Object> routes = new HashMap<>();
	
	public void get(String path, HttpHandler handler) {
		routes.put(path, handler);
	}
	
	//TODO Ugly - make it better
	public void getAsync(String path, AsyncHttpHandler handler) {
		routes.put(path, handler);
	}
	
	public Object route(HttpRequest request) {
        return routes.get(request.path);
    }
	
}
