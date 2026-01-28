package com.tefera.infra.gateway.server;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.tefera.infra.gateway.http.HttpRequest;

public class BackendContext {
	//public SocketChannel channel;
	
	public final ConnectionContext clientContext;
	public final SelectionKey clientKey;	
	
	public ByteBuffer writeBuffer = ByteBuffer.allocate(8192);
	public ByteBuffer readBuffer = ByteBuffer.allocate(8192);;
	
	//public HttpRequest originalRequest;


	
	//public boolean headersParsed = false;
	//public int contentLength = -1;
	
    public BackendContext(SelectionKey clientKey) {
        this.clientKey = clientKey;
        this.clientContext = (ConnectionContext) clientKey.attachment();
    }
}
