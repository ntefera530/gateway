package com.tefera.infra.gateway.http;

import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
	public int status;
	public String body;
	public Map<String, String> headers = new HashMap<>();
	
	public static HttpResponse ok(String body) {
		HttpResponse r = new HttpResponse();
		r.status = 200;
		r.body = body;
		return r;
	}
	
	public static HttpResponse badGateway(String body) {
		HttpResponse r = new HttpResponse();
		r.status = 502;
		r.body = body;
		return r;
	}
	
	public static HttpResponse notFound() {
		HttpResponse r = new HttpResponse();
		r.status = 404;
		r.body = "Not Found";
		return r;
	}
}
