package com.tefera.infra.gateway.server;

import java.nio.ByteBuffer;

public class ConnectionContext {
	public enum State{
		READING,
		WRITING,
		CLOSED
	}
	
	public final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
	public ByteBuffer writeBuffer;
	public State state = State.READING;
	
	public boolean keepAlive = true;

}
