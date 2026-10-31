/**
 * Token Bucket Rate Limiter
 * System Design Concept: Rate Limiting & Traffic Shaping
 */
class TokenBucketRateLimiter {
  constructor(maxTokens = 100, refillRatePerSec = 10) {
    this.maxTokens = maxTokens;
    this.refillRatePerSec = refillRatePerSec;
    this.buckets = new Map();

    // Clean up stale buckets every 5 minutes
    setInterval(() => this.cleanup(), 300000).unref();
  }

  allowRequest(key) {
    const now = Date.now();
    let bucket = this.buckets.get(key);

    if (!bucket) {
      bucket = {
        tokens: this.maxTokens - 1,
        lastRefill: now
      };
      this.buckets.set(key, bucket);
      return { allowed: true, remaining: bucket.tokens };
    }

    // Refill tokens based on elapsed time
    const elapsedSeconds = (now - bucket.lastRefill) / 1000;
    bucket.tokens = Math.min(this.maxTokens, bucket.tokens + elapsedSeconds * this.refillRatePerSec);
    bucket.lastRefill = now;

    if (bucket.tokens >= 1) {
      bucket.tokens -= 1;
      return { allowed: true, remaining: Math.floor(bucket.tokens) };
    }

    return { allowed: false, remaining: 0 };
  }

  cleanup() {
    const now = Date.now();
    for (const [key, bucket] of this.buckets.entries()) {
      // Remove buckets untouched for more than 10 minutes
      if (now - bucket.lastRefill > 600000) {
        this.buckets.delete(key);
      }
    }
  }
}

module.exports = TokenBucketRateLimiter;
