package com.tefera.infra.gateway.proxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.tefera.infra.gateway.http.AsyncHttpHandler;
import com.tefera.infra.gateway.http.HttpHandler;
import com.tefera.infra.gateway.http.HttpRequest;
import com.tefera.infra.gateway.http.HttpResponse;
import com.tefera.infra.gateway.server.BackendContext;

public class ProxyHandler implements AsyncHttpHandler {
	
	private final String host;
	private final int port;
	
	private static final int MAX_RESPONSE_BYTES = 1_000_000; //1mb
	private static final int READ_TIMEOUT_MS = 2000;
	private static final int CONNECT_TIMEOUT_MS = 2000;

	
	public ProxyHandler(String host, int port) {
		this.host = host;
		this.port = port;
	}
	
	@Override
	public void handleAsync(HttpRequest request, SelectionKey clientKey, Selector selector) throws IOException {
	    SocketChannel backend = SocketChannel.open();
	    backend.configureBlocking(false);
	    backend.connect(new InetSocketAddress(host, port));
	    
	    BackendContext backendContext = new BackendContext(clientKey);
	    
		String path = request.path;
		if(request.query != null) {
			path += "?" + request.query;
		}
		
	    String raw =
	            request.method + " " + path + " HTTP/1.1\r\n" +
	            "Host: " + host + "\r\n" +
	            "Connection: close\r\n" +
	            "\r\n";
	    
	    backendContext.writeBuffer = ByteBuffer.wrap(raw.getBytes(StandardCharsets.US_ASCII));
	    backend.register(selector, SelectionKey.OP_CONNECT, backendContext);
	    
	}
	
//	@Override
//	public HttpResponse handle(HttpRequest request) {
//		try(Socket socket = new Socket()){
//			
//			socket.connect(new InetSocketAddress(host,port), CONNECT_TIMEOUT_MS);
//			socket.setSoTimeout(READ_TIMEOUT_MS);
//
//			
//			OutputStream out = socket.getOutputStream();
//			InputStream in = socket.getInputStream();
//
//			//------ Build Requests ------------
//			String fullPath = request.path;
//			if(request.query != null) {
//				fullPath += "?" + request.query;
//			}
//			
//			String backendRequest = 
//					request.method + " " + fullPath + " HTTP/1.1\r\n" +
//					"Host: " + host + "\r\n" + 
//					"Connection: close\r\n" +
//					"\r\n";
//			
//			out.write(backendRequest.getBytes(StandardCharsets.US_ASCII));
//			out.flush();
//			
//			//---------- Read Response ----------
//			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
//			byte[] data = new byte[1024];
//			int total = 0;			
//			int n;
//
//			
//			while((n = in.read(data)) != -1) {
//				total += n;
//				if(total > MAX_RESPONSE_BYTES) {
//					return HttpResponse.badGateway("Response too large");
//				}
//				buffer.write(data, 0, n);
//			}
//			
//			String raw = buffer.toString(StandardCharsets.US_ASCII);
//			int headerEnd = raw.indexOf("\r\n\r\n");
//			if(headerEnd == -1) {
//				return HttpResponse.badGateway("Malformed backend response");
//			}
//			
//			String headerBlock = raw.substring(0,headerEnd);
//			String body = raw.substring(headerEnd + 4);
//			
//			String[] headerLines = headerBlock.split("\r\n");
//			String statusLine = headerLines[0];
//			String[] statusParts = statusLine.split(" ");
//			
//	        if (statusParts.length < 2 || !statusParts[0].startsWith("HTTP/")) {
//	            return HttpResponse.badGateway("Invalid status line");
//	        }
//	        
//			int status;
//			try {
//				status = Integer.parseInt(statusParts[1]);
//			}catch(NumberFormatException e) {
//				return HttpResponse.badGateway("Invalid status code");
//			}
//			
//			//--------Parse Headers-----------------------
//
//			Map<String, String> headers = new HashMap<>();
//			
//			for (int i = 1; i < headerLines.length; i++) {
//			    int colon = headerLines[i].indexOf(':');
//			    if (colon <= 0) continue;
//
//			    String name = headerLines[i].substring(0, colon).trim();
//			    String value = headerLines[i].substring(colon + 1).trim();
//			    
//			    //Skip hop-by-hop headers
//			    if(isHopByHopHeader(name)) continue;
//			    
//			    headers.put(name, value);
//			}
//			
//			//--------- Content-Length Validation --------------
//			
//			String cl = headers.get("Content-Length");
//			if (cl != null) {
//			    try {
//			        int expected = Integer.parseInt(cl);
//			        int actual = body.getBytes(StandardCharsets.US_ASCII).length;
//			        if (actual < expected) {
//			            return HttpResponse.badGateway("Truncated backend response");
//			        }
//			        
//			        //cut off excess for simplicty -- TODO reuse connections +  handle overflow
//			        if (actual > expected) {
//			            body = body.substring(0, expected);
//			        }
//			    } catch (NumberFormatException ignored) {}
//			}
//
//			//------ Build Response -------------
//			HttpResponse r = new HttpResponse();
//			r.status = status;
//			r.body = body;
//			r.headers = headers;
//			return r;
//            
//		}catch (IOException e) {
//			return HttpResponse.badGateway("Backend unreachable");
//		}
//	}
	
    private boolean isHopByHopHeader(String name) {
        return name.equalsIgnoreCase("Connection") ||
               name.equalsIgnoreCase("Keep-Alive") ||
               name.equalsIgnoreCase("Transfer-Encoding") ||
               name.equalsIgnoreCase("Upgrade");
    }
}
