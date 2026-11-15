module.exports = {
  port: parseInt(process.env.PORT || '8081', 10),
  redis: {
    url: process.env.REDIS_URL || 'redis://localhost:6379',
    geoKey: 'drivers:available',
    presenceKeyPrefix: 'driver:presence:',
    metadataKeyPrefix: 'driver:meta:',
    presenceTtlSec: parseInt(process.env.PRESENCE_TTL_SEC || '10', 10)
  },
  jwtSecret: process.env.JWT_SECRET || 'dGhpcy1pcy1hLXNlY3VyZS0yNTYtYml0LXNlY3JldC1rZXktZm9yLXJpZGUtc2hhcmluZy1wbGF0Zm9ybQ==',
  heartbeatIntervalMs: parseInt(process.env.HEARTBEAT_INTERVAL_MS || '5000', 10)
};
