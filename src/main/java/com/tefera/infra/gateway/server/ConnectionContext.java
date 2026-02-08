package com.tefera.infra.gateway.server;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.tefera.infra.gateway.http.HttpParser;
import com.tefera.infra.gateway.http.HttpRequest;
import com.tefera.infra.gateway.ratelimit.RateLimiter;
import com.tefera.infra.gateway.routing.Backend;

public class ConnectionContext {
	
	long id; 
	
	//Channels
	SocketChannel backend;
	
	//Selection Keys
	SelectionKey clientKey;
	SelectionKey backendKey;
	
	//Buffers
	ByteBuffer clientToBackend = ByteBuffer.allocateDirect(16 * 1024);
	ByteBuffer backendToClient = ByteBuffer.allocateDirect(16 * 1024);
	
	//Lifecycle
	boolean clientClosed = false;
	boolean backendClosed = false;
	boolean closed;
	
	// Response state
    boolean responseStarted;
    boolean responseFinished;
    
    
	boolean earlyResponse;
	
    //client info
	InetAddress clientIp;
	RateLimiter inboundLimiter;
	

	//HTTP parsing
    HttpParser parser = new HttpParser();
	HttpRequest request; // Headers only	
    boolean headersParsed = false;
	
    // Metrics
    long bytesFromClient;
    long bytesFromBackend;

    // Timeouts
    long lastActivityTime;
    
    Backend backendTarget;
    
    
	//Future L7 Hooks
//	boolean inspected = false;
//	ByteBuffer inspectionBuffer = ByteBuffer.allocate(8 * 1024);
	

}
