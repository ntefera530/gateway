package com.tefera.infra.gateway.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

import com.tefera.infra.gateway.http.AsyncHttpHandler;
import com.tefera.infra.gateway.http.HttpHandler;
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
		
		//router.get("/health", req -> HttpResponse.ok("OK"));
		//router.get("/hello", req -> HttpResponse.ok("Hello from gateway"));	
		
		//Test Proxy
		//router.get("/api", new ProxyHandler("localhost", 9000));
		router.getAsync("/api", new ProxyHandler("localhost", 9000));
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
			        } 
			        else if (key.isConnectable()) {
			        		handleBackendConnect(key);
			        }
			        else if (key.isReadable()) {
			        		if (key.attachment() instanceof BackendContext) {
			        		    handleBackendRead(key);
			        		} else {
			        			handleRead(key, selector);
			        		}
			        } else if (key.isWritable()) {
			        	    if (key.attachment() instanceof BackendContext) {
			        		        handleBackendWrite(key);
			        		    } else {
			        		        handleWrite(key);
			        		}
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
	
	private void handleRead(SelectionKey clientKey, Selector selector) throws IOException {
		System.out.println("Read");
		SocketChannel channel = (SocketChannel) clientKey.channel();
		ConnectionContext context = (ConnectionContext) clientKey.attachment();
		
		
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
		
		Object handler = router.route(request);
		
		if (handler instanceof HttpHandler sync) {
		    HttpResponse response = sync.handle(request);
		    writeResponse(clientKey, context, response);
		    return;
		}
		
		if (handler instanceof AsyncHttpHandler async) {
		    // pause client socket until backend responds
		    clientKey.interestOps(0);
		    async.handleAsync(request, clientKey, selector);
		    return;
		}
		
	}
	
	private void writeResponse(SelectionKey key, ConnectionContext context, HttpResponse response) {
		String body = response.body == null ? "" : response.body;
		
	    String raw =
	    		"HTTP/1.1 " + response.status + " " + statusText(response.status) + "\r\n" +
			"Content-Length: " + body.length() + "\r\n" +
			"Connection: " + (context.keepAlive ? "keep-alive" : "close") + "\r\n" +
			 "\r\n" +
			 body;
			
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
	
	private void handleBackendConnect(SelectionKey key) throws IOException {
	    SocketChannel backend = (SocketChannel) key.channel();
	    BackendContext ctx = (BackendContext) key.attachment();

	    if (backend.finishConnect()) {
	        key.interestOps(SelectionKey.OP_WRITE);
	    }
	}
	
	private void handleBackendWrite(SelectionKey key) throws IOException {
	    SocketChannel backend = (SocketChannel) key.channel();
	    BackendContext ctx = (BackendContext) key.attachment();

	    backend.write(ctx.writeBuffer);

	    if (!ctx.writeBuffer.hasRemaining()) {
	        key.interestOps(SelectionKey.OP_READ);
	    }
	}
	
	private void handleBackendRead(SelectionKey key) throws IOException {
	    SocketChannel backend = (SocketChannel) key.channel();
	    BackendContext backendCtx = (BackendContext) key.attachment();

	    int n = backend.read(backendCtx.readBuffer);
	    if (n == -1) {
	        backend.close();
	        key.cancel();

	        backendCtx.readBuffer.flip();
	        byte[] bytes = new byte[backendCtx.readBuffer.remaining()];
	        backendCtx.readBuffer.get(bytes);

	        String raw = new String(bytes, StandardCharsets.US_ASCII);

	        int split = raw.indexOf("\r\n\r\n");
	        String body = split == -1 ? "" : raw.substring(split + 4);

	        HttpResponse resp = HttpResponse.ok(body);

	        writeResponse(
	            backendCtx.clientKey,
	            backendCtx.clientContext,
	            resp
	        );

	        return;
	    }
	}
}
