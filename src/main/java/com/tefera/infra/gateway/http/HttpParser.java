package com.tefera.infra.gateway.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;


public class HttpParser {

    private static final byte CR = '\r';
    private static final byte LF = '\n';

    public ParseResult parse(ByteBuffer buffer) {

        int startPos = buffer.position();
        int limit = buffer.limit();

        // Look for \r\n\r\n
        for (int i = startPos; i + 3 < limit; i++) {
            if (buffer.get(i)     == CR &&
                buffer.get(i + 1) == LF &&
                buffer.get(i + 2) == CR &&
                buffer.get(i + 3) == LF) {

                int headersEnd = i + 4;

                HttpRequest request = parseHeaders(buffer, startPos, i);
                if (request == null) {
                    return ParseResult.error();
                }

                return ParseResult.complete(request, headersEnd - startPos);
            }
        }

        return ParseResult.incomplete();
    }

    private HttpRequest parseHeaders(ByteBuffer buffer, int start, int end) {
        byte[] data = new byte[end - start];
        buffer.get(start, data);

        String headers = new String(data, StandardCharsets.US_ASCII);
        String[] lines = headers.split("\r\n");

        if (lines.length == 0) return null;

        // ---- Request line ----
        String[] parts = lines[0].split(" ");
        if (parts.length != 3) return null;

        HttpRequest req = new HttpRequest(
            parts[0],  // method
            parts[1],  // path
            parts[2]   // version
        );

        // ---- Headers ----
        for (int i = 1; i < lines.length; i++) {
            int idx = lines[i].indexOf(':');
            if (idx <= 0) continue;

            String name = lines[i].substring(0, idx).trim();
            String value = lines[i].substring(idx + 1).trim();
            req.addHeader(name, value);
        }

        return req;
    }
}