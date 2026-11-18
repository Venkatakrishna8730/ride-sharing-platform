const http = require('http');
const url = require('url');
const fs = require('fs');
const path = require('path');
const config = require('./config');
const locationRepo = require('./redis');
const wsServer = require('./wsServer');

const PUBLIC_DIR = path.join(__dirname, '..', 'public');

function sendJson(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization'
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer(async (req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  const method = req.method;

  // Handle CORS Preflight
  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization'
    });
    res.end();
    return;
  }

  // 1. Health Probe
  if (pathname === '/health' && method === 'GET') {
    try {
      const redisHealthy = await locationRepo.healthCheck();
      const status = redisHealthy ? 'UP' : 'DEGRADED';
      sendJson(res, redisHealthy ? 200 : 503, {
        status,
        service: 'driver-location-service',
        redis: redisHealthy ? 'CONNECTED' : 'DISCONNECTED',
        activeWebSockets: wsServer.getActiveConnectionsCount(),
        timestamp: new Date().toISOString()
      });
    } catch (err) {
      sendJson(res, 503, {
        status: 'DOWN',
        error: err.message,
        timestamp: new Date().toISOString()
      });
    }
    return;
  }

  // 2. Query Nearby Drivers: GET /api/v1/drivers/nearby?latitude=...&longitude=...&radiusKm=5&limit=20
  if (pathname === '/api/v1/drivers/nearby' && method === 'GET') {
    const latStr = parsedUrl.query.latitude || parsedUrl.query.lat;
    const lonStr = parsedUrl.query.longitude || parsedUrl.query.lon;
    const radiusStr = parsedUrl.query.radiusKm || parsedUrl.query.radius || '5';
    const limitStr = parsedUrl.query.limit || '20';

    if (!latStr || !lonStr) {
      sendJson(res, 400, {
        status: 400,
        error: 'Bad Request',
        message: 'Query parameters latitude and longitude are required'
      });
      return;
    }

    const lat = parseFloat(latStr);
    const lon = parseFloat(lonStr);
    const radiusKm = parseFloat(radiusStr);
    const limit = parseInt(limitStr, 10);

    if (isNaN(lat) || isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      sendJson(res, 400, {
        status: 400,
        error: 'Bad Request',
        message: 'Invalid latitude [-90, 90] or longitude [-180, 180] values'
      });
      return;
    }

    const startTime = process.hrtime.bigint();
    try {
      const drivers = await locationRepo.getNearbyDrivers(lon, lat, radiusKm, limit);
      const endTime = process.hrtime.bigint();
      const durationMs = Number(endTime - startTime) / 1e6;

      sendJson(res, 200, {
        center: { latitude: lat, longitude: lon },
        radiusKm,
        count: drivers.length,
        queryExecutionTimeMs: parseFloat(durationMs.toFixed(3)),
        drivers
      });
    } catch (err) {
      console.error('[Nearby Query Error]', err);
      sendJson(res, 500, {
        status: 500,
        error: 'Internal Server Error',
        message: err.message
      });
    }
    return;
  }

  // 3. Single Driver Location Lookup: GET /api/v1/drivers/:driverId/location
  const singleDriverMatch = pathname.match(/^\/api\/v1\/drivers\/([a-zA-Z0-9_-]+)\/location$/);
  if (singleDriverMatch && method === 'GET') {
    const driverId = singleDriverMatch[1];
    try {
      const location = await locationRepo.getDriverLocation(driverId);
      if (!location) {
        sendJson(res, 404, {
          status: 404,
          error: 'Not Found',
          message: `Driver ${driverId} is offline or not found`
        });
        return;
      }
      sendJson(res, 200, location);
    } catch (err) {
      sendJson(res, 500, {
        status: 500,
        error: 'Internal Server Error',
        message: err.message
      });
    }
    return;
  }

  // 4. Serve Moving Map UI
  if ((pathname === '/' || pathname === '/map' || pathname === '/index.html') && method === 'GET') {
    const filePath = path.join(PUBLIC_DIR, 'index.html');
    if (fs.existsSync(filePath)) {
      res.writeHead(200, { 'Content-Type': 'text/html' });
      fs.createReadStream(filePath).pipe(res);
      return;
    }
  }

  // 404 Fallback
  sendJson(res, 404, {
    status: 404,
    error: 'Not Found',
    message: `Cannot ${method} ${pathname}`
  });
});

// Attach WebSocket server & HTTP Upgrade routing
wsServer.attach(server);

server.on('upgrade', (req, socket, head) => {
  wsServer.handleUpgrade(req, socket, head);
});

server.listen(config.port, () => {
  console.log(`[Driver Location Service] Listening on port ${config.port}`);
  console.log(`[Driver Location Service] WebSocket endpoint ready at ws://localhost:${config.port}/ws/driver/location`);
  console.log(`[Driver Location Service] Real-time map accessible at http://localhost:${config.port}/map`);
});

const gracefulShutdown = () => {
  console.log('[Driver Location Service] Shutting down gracefully...');
  wsServer.close();
  server.close(() => {
    locationRepo.disconnect().then(() => {
      console.log('[Driver Location Service] Disconnected from Redis and closed HTTP server');
      process.exit(0);
    });
  });
};

process.on('SIGTERM', gracefulShutdown);
process.on('SIGINT', gracefulShutdown);
