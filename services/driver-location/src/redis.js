const Redis = require('ioredis');
const config = require('./config');

class LocationRepository {
  constructor() {
    this.client = new Redis(config.redis.url, {
      maxRetriesPerRequest: 3,
      retryStrategy(times) {
        return Math.min(times * 100, 3000);
      }
    });

    this.client.on('error', (err) => {
      console.error('[Redis Error]', err.message);
    });

    this.client.on('connect', () => {
      console.log('[Redis] Connected successfully to', config.redis.url);
    });
  }

  /**
   * High-throughput atomic ingestion using Redis pipelines.
   * Updates GEO coordinates, sets presence key with TTL, and stores telemetry metadata.
   */
  async updateDriverLocation(driverId, lon, lat, bearing = 0, speed = 0, status = 'AVAILABLE') {
    const pipeline = this.client.pipeline();
    const presenceKey = `${config.redis.presenceKeyPrefix}${driverId}`;
    const metaKey = `${config.redis.metadataKeyPrefix}${driverId}`;

    pipeline.geoadd(config.redis.geoKey, lon, lat, driverId);
    pipeline.set(presenceKey, '1', 'EX', config.redis.presenceTtlSec);
    pipeline.set(
      metaKey,
      JSON.stringify({
        bearing: parseFloat(bearing) || 0,
        speed: parseFloat(speed) || 0,
        status: status || 'AVAILABLE',
        updatedAt: Date.now()
      }),
      'EX',
      config.redis.presenceTtlSec
    );

    await pipeline.exec();
  }

  /**
   * Geospatial search with TTL presence verification and automatic lazy eviction of dead drivers.
   */
  async getNearbyDrivers(lon, lat, radiusKm = 5, limit = 20) {
    // GEOSEARCH drivers:available FROMLONLAT <lon> <lat> BYRADIUS <radius> km WITHDIST WITHCOORD COUNT <limit> ASC
    const rawResults = await this.client.geosearch(
      config.redis.geoKey,
      'FROMLONLAT',
      lon,
      lat,
      'BYRADIUS',
      radiusKm,
      'km',
      'WITHDIST',
      'WITHCOORD',
      'COUNT',
      limit,
      'ASC'
    );

    if (!rawResults || rawResults.length === 0) {
      return [];
    }

    // Verify TTL presence for candidate drivers
    const driverIds = rawResults.map((r) => r[0]);
    const presenceKeys = driverIds.map((id) => `${config.redis.presenceKeyPrefix}${id}`);
    const metaKeys = driverIds.map((id) => `${config.redis.metadataKeyPrefix}${id}`);

    const [presenceFlags, metadataList] = await Promise.all([
      this.client.mget(presenceKeys),
      this.client.mget(metaKeys)
    ]);

    const activeDrivers = [];
    const staleDriverIds = [];

    for (let i = 0; i < rawResults.length; i++) {
      const [driverId, distStr, [driverLon, driverLat]] = rawResults[i];
      const isPresent = presenceFlags[i] !== null;

      if (isPresent) {
        let meta = {};
        if (metadataList[i]) {
          try {
            meta = JSON.parse(metadataList[i]);
          } catch (_) {}
        }

        activeDrivers.push({
          driverId,
          distanceKm: parseFloat(parseFloat(distStr).toFixed(2)),
          longitude: parseFloat(driverLon),
          latitude: parseFloat(driverLat),
          bearing: meta.bearing || 0,
          speed: meta.speed || 0,
          status: meta.status || 'AVAILABLE',
          lastSeenAt: meta.updatedAt || Date.now()
        });
      } else {
        staleDriverIds.push(driverId);
      }
    }

    // Lazy cleanup of stale drivers from GEO set without blocking query return
    if (staleDriverIds.length > 0) {
      this.client.zrem(config.redis.geoKey, ...staleDriverIds).catch((err) => {
        console.warn('[Redis Cleanup] Failed to evict stale drivers:', err.message);
      });
    }

    return activeDrivers;
  }

  /**
   * Retrieves single driver location and presence.
   */
  async getDriverLocation(driverId) {
    const presenceKey = `${config.redis.presenceKeyPrefix}${driverId}`;
    const metaKey = `${config.redis.metadataKeyPrefix}${driverId}`;

    const [presence, positions, rawMeta] = await Promise.all([
      this.client.get(presenceKey),
      this.client.geopos(config.redis.geoKey, driverId),
      this.client.get(metaKey)
    ]);

    if (!presence || !positions || !positions[0]) {
      return null;
    }

    const [lon, lat] = positions[0];
    let meta = {};
    if (rawMeta) {
      try {
        meta = JSON.parse(rawMeta);
      } catch (_) {}
    }

    return {
      driverId,
      longitude: parseFloat(lon),
      latitude: parseFloat(lat),
      bearing: meta.bearing || 0,
      speed: meta.speed || 0,
      status: meta.status || 'AVAILABLE',
      isOnline: true,
      lastSeenAt: meta.updatedAt || Date.now()
    };
  }

  /**
   * Removes driver explicitly (e.g. driver goes offline).
   */
  async removeDriver(driverId) {
    const pipeline = this.client.pipeline();
    pipeline.zrem(config.redis.geoKey, driverId);
    pipeline.del(`${config.redis.presenceKeyPrefix}${driverId}`);
    pipeline.del(`${config.redis.metadataKeyPrefix}${driverId}`);
    await pipeline.exec();
  }

  async healthCheck() {
    const reply = await this.client.ping();
    return reply === 'PONG';
  }

  async disconnect() {
    await this.client.quit();
  }
}

module.exports = new LocationRepository();
