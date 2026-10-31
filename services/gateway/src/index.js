const http = require('http');
const crypto = require('crypto');
const config = require('./config');
const { verifyJwt } = require('./jwt');
const TokenBucketRateLimiter = require('./rateLimiter');
const { forwardRequest } = require('./proxy');

const rateLimiter = new TokenBucketRateLimiter(
  config.rateLimit.maxTokens,
  config.rateLimit.refillRatePerSec
);

// Routes that do not require JWT validation
const PUBLIC_PREFIXES = [
  '/health',
  '/api/v1/auth/register',
  '/api/v1/auth/login'
];

function isPublicRoute(pathname) {
  return PUBLIC_PREFIXES.some(prefix => pathname === prefix || pathname.startsWith(prefix + '/'));
}

const server = http.createServer((req, res) => {
  const startTime = Date.now();
  const requestId = req.headers['x-request-id'] || crypto.randomUUID();
  res.setHeader('X-Request-Id', requestId);

  // 1. Health check endpoint
  if (req.url === '/health' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'UP', service: 'api-gateway', timestamp: new Date().toISOString() }));
    return;
  }

  // 2. Rate Limiting Check
  const clientIp = req.socket.remoteAddress || '127.0.0.1';
  const rateLimitResult = rateLimiter.allowRequest(clientIp);
  res.setHeader('X-RateLimit-Remaining', rateLimitResult.remaining);

  if (!rateLimitResult.allowed) {
    res.writeHead(429, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 429,
      error: 'Too Many Requests',
      message: 'Rate limit exceeded. Please retry shortly.',
      requestId: requestId,
      timestamp: new Date().toISOString()
    }));
    return;
  }

  // 3. JWT Authentication & Context Propagation
  let extraHeaders = {
    'x-request-id': requestId
  };

  const pathname = req.url.split('?')[0];

  if (!isPublicRoute(pathname)) {
    const authHeader = req.headers['authorization'];
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: 401,
        error: 'Unauthorized',
        message: 'Missing or invalid Authorization header. Expected Bearer token.',
        path: req.url,
        timestamp: new Date().toISOString()
      }));
      return;
    }

    const token = authHeader.substring(7);
    try {
      const claims = verifyJwt(token, config.jwtSecret);
      extraHeaders['x-user-id'] = claims.sub;
      extraHeaders['x-user-role'] = claims.role || 'RIDER';
      extraHeaders['x-user-email'] = claims.email || '';
    } catch (err) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: 401,
        error: 'Unauthorized',
        message: 'Invalid or expired token: ' + err.message,
        path: req.url,
        timestamp: new Date().toISOString()
      }));
      return;
    }
  }

  // 4. Request Logging
  res.on('finish', () => {
    const durationMs = Date.now() - startTime;
    console.log(JSON.stringify({
      timestamp: new Date().toISOString(),
      requestId,
      method: req.method,
      path: req.url,
      statusCode: res.statusCode,
      durationMs,
      clientIp
    }));
  });

  // 5. Reverse Proxy Routing
  if (pathname.startsWith('/api/v1/auth') || pathname.startsWith('/api/v1/users')) {
    forwardRequest(config.services.userAuth, req, res, extraHeaders);
  } else if (pathname.startsWith('/api/v1/trips')) {
    forwardRequest(config.services.trip, req, res, extraHeaders);
  } else {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 404,
      error: 'Not Found',
      message: `No route registered on API Gateway for path: ${req.url}`,
      requestId: requestId,
      timestamp: new Date().toISOString()
    }));
  }
});

server.listen(config.port, () => {
  console.log(`[API Gateway] Listening on port ${config.port}`);
  console.log(`[API Gateway] Routing /api/v1/auth & /api/v1/users -> ${config.services.userAuth}`);
  console.log(`[API Gateway] Routing /api/v1/trips -> ${config.services.trip}`);
});

process.on('SIGTERM', () => {
  console.log('[API Gateway] SIGTERM received. Shutting down gracefully...');
  server.close(() => process.exit(0));
});
