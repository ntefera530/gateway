package com.tefera.infra.gateway.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;


public class HttpParser {

	public ParseResult tryParse(ByteBuffer buffer) {
		int limit = buffer.position();
		
		for(int i = 3; i < limit; i++) {
			if(
					buffer.get(i - 3) == '\r'
					&& buffer.get(i - 2) == '\n'
					&& buffer.get(i - 1) == '\r'
					&& buffer.get(i) == '\n'
			){
				int headersEnd = i + 1;
				HttpRequest request = parseRequest(buffer, headersEnd);
				return new ParseResult(request, headersEnd);
			}
		}
		
		return null;
	}
	
	private HttpRequest parseRequest(ByteBuffer buffer, int length) {
		String raw = new String(buffer.array(), 0, length, StandardCharsets.US_ASCII);
		String[] lines = raw.split("\r\n");
		
		if(lines.length == 0) {
			return null;
		}
		
		String[] requestLine = lines[0].split(" ");
		if(requestLine.length != 3) {
			return null;
		}
		
		HttpRequest req = new HttpRequest();
		req.method = requestLine[0];
		req.path = requestLine[1];
		req.version = requestLine[2];
		
		for(int i = 1; i < lines.length; i++) {
			String line = lines[i];
			if(line.isEmpty()) {
				break;
			}
			
			int colon = line.indexOf(':');
			if(colon == -1) {
				continue;
			}
			
			String name = line.substring(0, colon).trim();
			String value = line.substring(colon + 1).trim();
			req.headers.put(name, value);
			
			
		}
		
		return req;
	}
}
