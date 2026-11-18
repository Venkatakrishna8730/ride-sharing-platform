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

/**
 * Forwards HTTP Upgrade requests (WebSocket) to upstream backend.
 */
function forwardWebSocketUpgrade(targetBaseUrl, req, clientSocket, head) {
  const targetParsed = url.parse(targetBaseUrl);
  const targetPort = targetParsed.port || (targetParsed.protocol === 'https:' ? 443 : 80);
  const targetHost = targetParsed.hostname;

  const headers = {
    ...req.headers,
    host: targetParsed.host
  };

  const options = {
    hostname: targetHost,
    port: targetPort,
    path: req.url,
    method: req.method,
    headers: headers
  };

  const proxyReq = http.request(options);

  proxyReq.on('upgrade', (proxyRes, proxySocket, proxyHead) => {
    let responseHeaders = `HTTP/1.1 101 Switching Protocols\r\n`;
    for (const [key, value] of Object.entries(proxyRes.headers)) {
      if (Array.isArray(value)) {
        for (const v of value) {
          responseHeaders += `${key}: ${v}\r\n`;
        }
      } else {
        responseHeaders += `${key}: ${value}\r\n`;
      }
    }
    responseHeaders += '\r\n';

    clientSocket.write(responseHeaders);
    if (proxyHead && proxyHead.length) clientSocket.write(proxyHead);
    if (head && head.length) proxySocket.write(head);

    proxySocket.pipe(clientSocket);
    clientSocket.pipe(proxySocket);

    proxySocket.on('error', () => {
      clientSocket.destroy();
    });
    clientSocket.on('error', () => {
      proxySocket.destroy();
    });
  });

  proxyReq.on('error', (err) => {
    console.error(`[Gateway WS Proxy Error] Failed to reach ${targetBaseUrl}:`, err.message);
    clientSocket.destroy();
  });

  proxyReq.end();
}

module.exports = {
  forwardRequest,
  forwardWebSocketUpgrade
};
