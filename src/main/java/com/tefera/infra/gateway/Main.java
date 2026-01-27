package com.tefera.infra.gateway;

import com.tefera.infra.gateway.server.*;

public class Main {
	public static void main(String args[]) throws Exception {
		System.out.println("Gateway is starting...");
		
		NioServer server = new NioServer(8080);
		server.start();
	}
}
