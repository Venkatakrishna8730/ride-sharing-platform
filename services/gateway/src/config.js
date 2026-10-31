module.exports = {
  port: parseInt(process.env.PORT || '8080', 10),
  jwtSecret: process.env.JWT_SECRET || 'dGhpcy1pcy1hLXNlY3VyZS0yNTYtYml0LXNlY3JldC1rZXktZm9yLXJpZGUtc2hhcmluZy1wbGF0Zm9ybQ==',
  services: {
    userAuth: process.env.USER_AUTH_URL || 'http://localhost:8082',
    trip: process.env.TRIP_URL || 'http://localhost:8083'
  },
  rateLimit: {
    maxTokens: parseInt(process.env.RATE_LIMIT_MAX || '100', 10),
    refillRatePerSec: parseFloat(process.env.RATE_LIMIT_REFILL_RATE || '10')
  }
};
