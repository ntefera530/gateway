package com.tefera.infra.gateway.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.tefera.infra.gateway.http.HttpHandler;
import com.tefera.infra.gateway.http.HttpRequest;
import com.tefera.infra.gateway.http.HttpResponse;

public class ProxyHandler implements HttpHandler {
	
	private final String host;
	private final int port;
	
	public ProxyHandler(String host, int port) {
		this.host = host;
		this.port = port;
	}
	
	@Override
	public HttpResponse handle(HttpRequest request) {
		try(Socket socket = new Socket(host, port)){
			OutputStream out = socket.getOutputStream();
			InputStream in = socket.getInputStream();
			
			//forwards request
			String req = 
					request.method + " " + request.path + " HTTP/1.1\r\n" +
					"Host: " + host + "\r\n" + 
					"Connection: close\r\n" +
					"\r\n";
			
			out.write(req.getBytes());
			out.flush();
			
			//Read Full response
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] data = new byte[1024];
			int n;
			
			while((n = in.read(data)) != -1) {
				buffer.write(data, 0, n);
			}
			
			String raw = buffer.toString();
			
            //Very Basic Parsing (OK for MVP)
            String body = raw.split("\r\n\r\n", 2)[1];
            
            return HttpResponse.ok(body);
            
		}catch (IOException e) {
            HttpResponse r = new HttpResponse();
            r.status = 502;
            r.body = "Bad Gateway";
            return r;
		}
	}
}
