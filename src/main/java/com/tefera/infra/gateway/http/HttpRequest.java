package com.tefera.infra.gateway.http;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {

    public final String method;
    public final String path;
    public final String query;
    public final String version;

    public final Map<String, String> headers = new HashMap<>();

    public HttpRequest(String method, String rawPath, String version) {
        this.method = method;
        this.version = version;

        int q = rawPath.indexOf('?');
        if (q >= 0) {
            this.path = rawPath.substring(0, q);
            this.query = rawPath.substring(q + 1);
        } else {
            this.path = rawPath;
            this.query = null;
        }
    }

    public void addHeader(String name, String value) {
        headers.put(name.toLowerCase(), value);
    }

    public String header(String name) {
        return headers.get(name.toLowerCase());
    }
    
    public String getPath() {
        return path;
    }
}