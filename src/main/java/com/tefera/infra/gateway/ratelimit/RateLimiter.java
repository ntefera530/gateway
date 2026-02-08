package com.tefera.infra.gateway.ratelimit;

public class RateLimiter {
	private final long ratePerSec;
	private final long capacity;
	
	private long tokens;
	private long lastRefillNanos;
	
	public RateLimiter(long ratePerSec, long capacity) {
		this.ratePerSec = ratePerSec;
		this.capacity = capacity;
		this.tokens = capacity;
		this.lastRefillNanos = System.nanoTime();
	}
	
	public int acquire(int requestedBytes) {
		refill();
		
		if(tokens <= 0) {
			return 0;
		}
		
		int allowed = (int) Math.min(tokens, requestedBytes);
		tokens -= allowed;
		
		return allowed;
		
	}
	
	public void refill() {
		long now = System.nanoTime();
		long elapsed = now - lastRefillNanos;
		if(elapsed <= 0) {
			return;
		}
		
		long refill = (elapsed * ratePerSec) / 1_000_000_000L;
		if(refill > 0) {
			tokens = Math.min(capacity, tokens + refill);
			lastRefillNanos = now;
		}
	}
}
