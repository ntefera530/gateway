package com.tefera.infra.gateway.http;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public interface AsyncHttpHandler {
	void handleAsync(
			HttpRequest request,
			SelectionKey clientKey,
			Selector selector
	) throws IOException;
}
