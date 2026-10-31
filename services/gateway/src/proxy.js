const http = require('http');
const https = require('https');
const url = require('url');

/**
 * High-performance streaming reverse proxy forwarder.
 */
function forwardRequest(targetBaseUrl, req, res, extraHeaders = {}) {
  const targetParsed = url.parse(targetBaseUrl);
  const clientLib = targetParsed.protocol === 'https:' ? https : http;

  const headers = {
    ...req.headers,
    ...extraHeaders,
    host: targetParsed.host
  };

  const options = {
    hostname: targetParsed.hostname,
    port: targetParsed.port || (targetParsed.protocol === 'https:' ? 443 : 80),
    path: req.url,
    method: req.method,
    headers: headers
  };

  const proxyReq = clientLib.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res, { end: true });
  });

  proxyReq.on('error', (err) => {
    console.error(`[Gateway Proxy Error] Failed to reach ${targetBaseUrl}:`, err.message);
    if (!res.headersSent) {
      res.writeHead(503, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: 503,
        error: 'Service Unavailable',
        message: `Upstream service at ${targetBaseUrl} is unreachable`,
        path: req.url,
        timestamp: new Date().toISOString()
      }));
    }
  });

  req.pipe(proxyReq, { end: true });
}

module.exports = {
  forwardRequest
};
