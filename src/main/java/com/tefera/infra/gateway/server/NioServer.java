package com.tefera.infra.gateway.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

import com.tefera.infra.gateway.http.HttpParser;
import com.tefera.infra.gateway.http.HttpRequest;
import com.tefera.infra.gateway.http.HttpResponse;
import com.tefera.infra.gateway.http.ParseResult;
import com.tefera.infra.gateway.proxy.ProxyHandler;
import com.tefera.infra.gateway.routing.Router;

public class NioServer {
	private final int port;
	private final HttpParser parser = new HttpParser();
	private final Router router = new Router();
	
	public NioServer(int port) {
		this.port = port;
		
		router.get("/health", req -> HttpResponse.ok("OK"));
		router.get("/hello", req -> HttpResponse.ok("Hello from gateway"));	
		
		//Test Proxy
		router.get("/api", new ProxyHandler("localhost", 9000));
	}
	
	public void start() throws IOException {
		Selector selector = Selector.open();
		
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.bind(new InetSocketAddress(port));
		
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		
		System.out.println("NIO Server listening on port " + port);
		

		
		while(true) {
			selector.select();
			
			Set<SelectionKey> keys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = keys.iterator();
			
			while(iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				
			    if (!key.isValid()) {
			    	System.out.println("Not Valid");
			        continue;
			    }
			    
			    try {
			        if (key.isAcceptable()) {
			            handleAccept(key, selector);
			        } else if (key.isReadable()) {
			            handleRead(key);
			        } else if (key.isWritable()) {
			            handleWrite(key);
			        }
			    } catch (IOException e) {
			        key.cancel();
			        try {
			            key.channel().close();
			        } catch (IOException ignored) {}
			    }
			}	
		}
	}
	
	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
		SocketChannel clientChannel = serverChannel.accept();
		
		clientChannel.configureBlocking(false);
		
		ConnectionContext context = new ConnectionContext();
		clientChannel.register(
				selector,
				SelectionKey.OP_READ,
				context
		);
		
		
		System.out.println("Accepted connection from " + clientChannel.getRemoteAddress());
	}
	
	
	private void handleRead(SelectionKey key) throws IOException {
		System.out.println("Read");
		SocketChannel channel = (SocketChannel) key.channel();
		ConnectionContext context = (ConnectionContext) key.attachment();
		
		
		int bytesRead = channel.read(context.readBuffer);
		
		if(bytesRead == -1) {
			channel.close();
			return;
		}
		
		ParseResult result = parser.tryParse(context.readBuffer);
		if(result == null) {
			return;
		}
		
		HttpRequest request = result.request;


		String connectionHeader = request.headers.get("Connection");
		if("close".equalsIgnoreCase(connectionHeader)) {
			context.keepAlive = false;
		}
		
		String connectionValue = context.keepAlive ? "keep-alive" : "close";
		
		HttpResponse response = router.route(request);
		String body = response.body;
		String raw = 
				"HTTP/1.1 " + response.status + " " + statusText(response.status) + "\r\n" +
				"Content-length: " + body.length() + "\r\n" +
				"Connection: " + connectionValue + "\r\n" +
				"\r\n" +
				body;
		
		context.readBuffer.position(result.bytesConsumed);
		context.readBuffer.compact();
		
		context.writeBuffer = ByteBuffer.wrap(raw.getBytes());
		context.state = ConnectionContext.State.WRITING;
		
		key.interestOps(SelectionKey.OP_WRITE);
		
	}
	
	private String statusText(int status) {
		return switch(status) {
			case 200 -> "OK";
			case 404 -> "Not Found";
			default -> "Unknown";
		};
	}
	
	private void handleWrite(SelectionKey key) throws IOException {
		System.out.println("Write");
	    SocketChannel channel = (SocketChannel) key.channel();
	    ConnectionContext context = (ConnectionContext) key.attachment();

	    channel.write(context.writeBuffer);

	    if (!context.writeBuffer.hasRemaining()) {
	        if (context.keepAlive) {
	            // Ready for the next request
	            context.state = ConnectionContext.State.READING;
	            key.interestOps(SelectionKey.OP_READ);
	        } else {
	        	key.cancel();
	        	channel.close();
	        }
	    }
	    
	    
	}
}
