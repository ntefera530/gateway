package com.tefera.infra.gateway.server;

import java.io.IOException;
import java.net.InetAddress;
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
import com.tefera.infra.gateway.ratelimit.IpLimiter;
import com.tefera.infra.gateway.ratelimit.RateLimiter;

import com.tefera.infra.gateway.routing.Backend;

import com.tefera.infra.gateway.routing.Router;

public class NioServer {
	private final int port;
	//private final HttpParser parser = new HttpParser();
	private final Router router = new Router();
	
	public NioServer(int port) {
		this.port = port;
	}

	
	public void start() throws IOException {
		
		Selector selector = Selector.open();
		
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.bind(new InetSocketAddress(port));
		
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		
		System.out.println("NIO Server listening on port " + port);
		

		
		while (true) {
			
			//Blocks until event to handle
		    selector.select();
		    
		    //Gets list of Events to handle
		    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
		    
		    while (it.hasNext()) {
		    		
		    		//The socket event to handle 
		        SelectionKey key = it.next();
		        it.remove();

		        if (!key.isValid()) {
		        		continue;
		        }
		        
		        	//Create an Object to hold client and server conections. 		        
		        ConnectionContext ctx = (ConnectionContext) key.attachment();	
		        try {
		        	

		            
		            
		            if (key.isAcceptable()) {
		                handleAccept(key, selector);
		                continue;
		            }



		            if (key.isConnectable()) {
		                handleBackendConnect(key);
		            }

		            else if (key.isReadable()) {
		                if (key == ctx.clientKey) {
		                    handleRead(ctx.clientKey, selector);
		                } else {
		                    handleBackendRead(ctx.backendKey);
		                }
		            }

		            
		            else if (key.isWritable()) {
		                if (key == ctx.clientKey) {
		                    handleWrite(ctx.clientKey);
		                } else {
		                    handleBackendWrite(ctx.backendKey);
		                }
		            }
		            
		        } catch (IOException e) {
		        		cleanupContext(ctx);
		        }
		    }
		}
	}
	
	private void closeKey(SelectionKey key) { 
		if(key == null) { 
			return; 
		} 
		try { 
			key.cancel();
			key.channel().close(); 
		} catch (IOException ignored) { } 
		
	} 
	
	private void cleanupContext(ConnectionContext context) {
		if(context == null) {
			return;
		}
		closeKey(context.clientKey);
		closeKey(context.backendKey);
		
		context.clientKey = null;
		context.backendKey = null;
		context.backend = null;
		
	}
	
	//private void handleAccept(SelectionKey key, Selector selector) throws IOException {
	private void acceptClientConnection(SelectionKey key, Selector selector) throws IOException {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
		SocketChannel clientChannel = serverChannel.accept();
		
		clientChannel.configureBlocking(false);
		
		InetSocketAddress remote = (InetSocketAddress) clientChannel.getRemoteAddress();
		InetAddress clientIp = remote.getAddress();
		
		ConnectionContext context = new ConnectionContext();
		context.clientIp = clientIp;
		context.inboundLimiter = IpLimiter.get(clientIp);
		
		SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ, context);
		context.clientKey = clientKey;
		
		System.out.println("Accepted connection from " + clientIp);
	}
	
	//private void handleRead(SelectionKey clientKey, Selector selector) throws IOException {
	private void recieveClientData(SelectionKey clientKey, Selector selector) throws IOException {
		SocketChannel clientChannel = (SocketChannel) clientKey.channel();
		ConnectionContext context = (ConnectionContext) clientKey.attachment();
		
		Backend target = router.route(context.request);
		context.backendTarget = target;
		
		// Lazy Backend Connect
		if(context.backend == null) {
			SocketChannel backendChannel = SocketChannel.open();
			backendChannel.configureBlocking(false);
			//backendChannel.connect(new InetSocketAddress("localhost",9000));
			backendChannel.connect(new InetSocketAddress("backend",9000));
			
			
			
			context.backend = backendChannel;
			
			SelectionKey backendKey = backendChannel.register(clientKey.selector(), SelectionKey.OP_CONNECT, context);
			context.backendKey = backendKey;
		}
		
		
		RateLimiter limiter = context.inboundLimiter;
		
		int want = context.clientToBackend.remaining();
		int allowed = limiter.acquire(want);
		
		if (allowed == 0) {
			// Rate limit hit → apply backpressure
			clientKey.interestOps(clientKey.interestOps() & ~SelectionKey.OP_READ);
		     return;
		}
		
		int oldLimit = context.clientToBackend.limit();
		context.clientToBackend.limit(context.clientToBackend.position() + allowed);
		
		int n = clientChannel.read(context.clientToBackend);
		
		context.clientToBackend.limit(oldLimit);
		
		if(n == -1) {
			context.clientClosed = true;
			clientKey.interestOps(0);
			return;
		}
		
		//--READING HEADERS--------
		
	    if (!context.headersParsed) {
	        context.clientToBackend.flip();

	        ParseResult result = context.parser.parse(context.clientToBackend);

	        if (result.isError()) {
	            send400AndClose(context);
	            return;
	        }

	        if (result.isDone()) {
	            context.headersParsed = true;
	            context.request = result.getRequest();


	        }

	        context.clientToBackend.compact();
	    }	
		
		// Enable backend write if we have data
		if (context.clientToBackend.position() > 0) {
		    context.backendKey.interestOps(
		        context.backendKey.interestOps() | SelectionKey.OP_WRITE
		    );
		}

		// Apply backpressure correctly
		if (!context.clientToBackend.hasRemaining()) {
		    clientKey.interestOps(clientKey.interestOps() & ~SelectionKey.OP_READ);
		}
	}
	
	private void connectBackend(ConnectionContext ctx, Selector selector) throws IOException {
	    SocketChannel backendChannel = SocketChannel.open();
	    backendChannel.configureBlocking(false);

	    Backend b = ctx.backendTarget;

	    backendChannel.connect(
	        new InetSocketAddress(b.host, b.port)
	    );

	    ctx.backend = backendChannel;

	    SelectionKey backendKey =
	        backendChannel.register(selector, SelectionKey.OP_CONNECT, ctx);

	    ctx.backendKey = backendKey;
	}
	
	
	private void send400AndClose(ConnectionContext ctx) throws IOException {
	    ByteBuffer buf = ByteBuffer.wrap(
	        "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
	            .getBytes(StandardCharsets.US_ASCII)
	    );
	    SocketChannel ch = (SocketChannel) ctx.clientKey.channel();
	    while (buf.hasRemaining()) {
	        ch.write(buf);
	    }
	    cleanupContext(ctx);
	}
	
	//private void handleWrite(SelectionKey key) throws IOException {
	private void writeBackendResponseToClient(SelectionKey key) throws IOException {
	    SocketChannel client = (SocketChannel) key.channel();
	    ConnectionContext context = (ConnectionContext) key.attachment();

	    context.backendToClient.flip();
	    client.write(context.backendToClient);
	    context.backendToClient.compact();
	    
	    if (context.earlyResponse && context.backendToClient.position() == 0) {
	        try {
	    			context.backend.shutdownInput();
	    		} catch (IOException ignored) {}
	        cleanupContext(context);
	        return;
	    }
	    
	    if (context.backendToClient.position() == 0) {
	    	    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
	    	}	    
	    

	}
	
	//private void handleBackendConnect(SelectionKey key) throws IOException {
	private void backendConnected(SelectionKey key) throws IOException {
	    SocketChannel backendChannel = (SocketChannel) key.channel();
	    ConnectionContext context = (ConnectionContext) key.attachment();
	    
	    if (backendChannel.finishConnect()) {
	    		int ops = SelectionKey.OP_READ;
	    		if (context.clientToBackend.position() > 0) {
	    			ops |= SelectionKey.OP_WRITE;
	    	    }
	    	      key.interestOps(ops);;
	    }
	}
	
	//private void handleBackendWrite(SelectionKey key) throws IOException {
	private void writeClientDataToBackend(SelectionKey key) throws IOException {
	    ConnectionContext context = (ConnectionContext) key.attachment();  
	    SocketChannel backendChannel = context.backend;
	    
		if (context.earlyResponse) {
	        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
	        return;
	    }
	    context.clientToBackend.flip();
	    backendChannel.write(context.clientToBackend);
	    context.clientToBackend.compact();
	    

	    
	    
	    if (context.clientToBackend.hasRemaining()) {
	    		context.clientKey.interestOps( context.clientKey.interestOps() | SelectionKey.OP_READ);
	    	}    
	    
	    if (context.clientToBackend.position() == 0) {
	    	key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
	    }

	    if (context.clientClosed && context.clientToBackend.position() == 0) {
	    		backendChannel.shutdownOutput();
	    }
	    
	    key.interestOps(key.interestOps() | SelectionKey.OP_READ);
	}
	
	private void readBackendResponse(SelectionKey key) throws IOException {
	    ConnectionContext context = (ConnectionContext) key.attachment();
	    SocketChannel backendChannel = context.backend;

	    int n = backendChannel.read(context.backendToClient);
	    if (n == -1) {
	    	context.backendClosed = true;
	    		backendChannel.close();
	        return;
	    }
	    
	    //key.interestOps(key.interestOps() | SelectionKey.OP_READ);
	    if (!context.clientClosed) {
	        context.earlyResponse = true;
	        
	        context.clientKey.interestOps(
	            context.clientKey.interestOps() & ~SelectionKey.OP_READ
	        );
	        
	        context.backendKey.interestOps(
	        	    context.backendKey.interestOps() & ~SelectionKey.OP_WRITE
	        	);
	    }
	    
	    context.clientKey.interestOps(
	    		context.clientKey.interestOps() | SelectionKey.OP_WRITE
	    );
	}
}
