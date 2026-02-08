package com.tefera.infra.gateway.ratelimit;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

public class IpLimiter {
    private static final ConcurrentHashMap<InetAddress, RateLimiter> map =
        new ConcurrentHashMap<>();

    public static RateLimiter get(InetAddress ip) {
        return map.computeIfAbsent(
            ip,
            k -> new RateLimiter(2_000_000, 2_000_000)
        );
    }
}
