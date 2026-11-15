const { WebSocketServer, WebSocket } = require('ws');
const url = require('url');
const config = require('./config');
const { verifyJwt } = require('./jwt');
const locationRepo = require('./redis');

class DriverLocationWSServer {
  constructor() {
    this.wss = null;
    this.heartbeatTimer = null;
    this.activeSockets = new Map(); // driverId -> ws
  }

  attach(httpServer) {
    this.wss = new WebSocketServer({
      noServer: true
    });

    this.wss.on('connection', (ws, req, claims) => {
      this.handleConnection(ws, req, claims);
    });

    // Start heartbeat sweeper
    this.heartbeatTimer = setInterval(() => {
      this.sweepHeartbeats();
    }, config.heartbeatIntervalMs);

    console.log('[WebSocket Server] Driver Location WS Server initialized');
  }

  /**
   * Performs authentication during HTTP Upgrade handshake before socket creation.
   */
  handleUpgrade(req, socket, head) {
    const parsedUrl = url.parse(req.url, true);
    const pathname = parsedUrl.pathname;

    if (pathname !== '/ws/driver/location') {
      socket.write('HTTP/1.1 404 Not Found\r\n\r\n');
      socket.destroy();
      return;
    }

    const token = parsedUrl.query.token;
    if (!token) {
      console.warn('[WS Upgrade] Rejected: missing token query param');
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
      return;
    }

    try {
      const claims = verifyJwt(token, config.jwtSecret);
      this.wss.handleUpgrade(req, socket, head, (ws) => {
        this.wss.emit('connection', ws, req, claims);
      });
    } catch (err) {
      console.warn('[WS Upgrade] Authentication failed:', err.message);
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
    }
  }

  handleConnection(ws, req, claims) {
    const driverId = claims.sub;
    ws.isAlive = true;
    ws.driverId = driverId;

    // Disconnect any existing socket for the same driverId (prevent ghost duplicate sessions)
    if (this.activeSockets.has(driverId)) {
      const oldWs = this.activeSockets.get(driverId);
      try {
        oldWs.terminate();
      } catch (_) {}
    }
    this.activeSockets.set(driverId, ws);

    ws.on('pong', () => {
      ws.isAlive = true;
    });

    ws.on('message', async (data) => {
      try {
        const payload = JSON.parse(data.toString());
        const { latitude, longitude, bearing, speed, status } = payload;

        if (
          typeof latitude !== 'number' ||
          typeof longitude !== 'number' ||
          latitude < -90 ||
          latitude > 90 ||
          longitude < -180 ||
          longitude > 180
        ) {
          ws.send(JSON.stringify({ error: 'Invalid coordinates' }));
          return;
        }

        await locationRepo.updateDriverLocation(
          driverId,
          longitude,
          latitude,
          bearing,
          speed,
          status || 'AVAILABLE'
        );

        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ ack: true, timestamp: Date.now() }));
        }
      } catch (err) {
        console.error(`[WS Ingest Error] Driver ${driverId}:`, err.message);
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ error: 'Malformed location packet' }));
        }
      }
    });

    ws.on('close', () => {
      if (this.activeSockets.get(driverId) === ws) {
        this.activeSockets.delete(driverId);
      }
    });

    ws.on('error', (err) => {
      console.warn(`[WS Error] Driver ${driverId}:`, err.message);
    });

    // Send connection greeting
    ws.send(
      JSON.stringify({
        connected: true,
        driverId,
        message: 'Driver location stream active'
      })
    );
  }

  sweepHeartbeats() {
    if (!this.wss) return;

    this.wss.clients.forEach((ws) => {
      if (!ws.isAlive) {
        console.log(`[WS Heartbeat] Terminating inactive socket for driver ${ws.driverId}`);
        return ws.terminate();
      }
      ws.isAlive = false;
      ws.ping();
    });
  }

  getActiveConnectionsCount() {
    return this.activeSockets.size;
  }

  close() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
    }
    if (this.wss) {
      this.wss.close();
    }
  }
}

module.exports = new DriverLocationWSServer();
