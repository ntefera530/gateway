package com.tefera.infra.gateway.routing;

import java.util.HashMap;
import java.util.Map;

import com.tefera.infra.gateway.http.AsyncHttpHandler;
import com.tefera.infra.gateway.http.HttpHandler;
import com.tefera.infra.gateway.http.HttpRequest;
import com.tefera.infra.gateway.http.HttpResponse;


public class Router {
	public Backend route(HttpRequest req) {
        String path = req.getPath();

        if (path.startsWith("/users")) {
            return new Backend("users-service", 9000);
        }

        if (path.startsWith("/orders")) {
            return new Backend("orders-service", 9000);
        }

        return new Backend("default-service", 9000);
	}
}
