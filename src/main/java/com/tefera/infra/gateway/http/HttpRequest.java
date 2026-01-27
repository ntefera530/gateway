package com.tefera.infra.gateway.http;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
	public String method;
	public String path;
	public String version;
	public final Map<String, String> headers = new HashMap<>();
}
