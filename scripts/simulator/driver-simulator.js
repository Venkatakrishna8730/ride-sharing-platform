const { WebSocket } = require('ws');
const crypto = require('crypto');

// Parse CLI flags: --drivers=1000 --target=ws://localhost:8080/ws/driver/location --interval=2000 --duration=60
const args = process.argv.slice(2).reduce((acc, arg) => {
  const [k, v] = arg.split('=');
  acc[k.replace(/^--/, '')] = v;
  return acc;
}, {});

const DRIVER_COUNT = parseInt(args.drivers || '500', 10);
const TARGET_WS_URL = args.target || 'ws://localhost:8080/ws/driver/location';
const UPDATE_INTERVAL_MS = parseInt(args.interval || '2500', 10);
const DURATION_SEC = parseInt(args.duration || '0', 10); // 0 = infinite
const KILL_AFTER_SEC = parseInt(args['kill-after'] || '0', 10);
const KILL_RATIO = parseFloat(args['kill-ratio'] || '0.25');

const JWT_SECRET = process.env.JWT_SECRET || 'dGhpcy1pcy1hLXNlY3VyZS0yNTYtYml0LXNlY3JldC1rZXktZm9yLXJpZGUtc2hhcmluZy1wbGF0Zm9ybQ==';

// Base coordinates (Bangalore center)
const BASE_LAT = 12.9716;
const BASE_LON = 77.5946;

function generateDriverToken(driverId) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS512', typ: 'JWT' })).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const payload = Buffer.from(
    JSON.stringify({
      sub: driverId,
      email: `${driverId}@driver.rideplatform.com`,
      role: 'DRIVER',
      iat: now,
      exp: now + 86400
    })
  ).toString('base64url');

  const hmac = crypto.createHmac('sha512', JWT_SECRET);
  hmac.update(`${header}.${payload}`);
  const signature = hmac.digest('base64url');

  return `${header}.${payload}.${signature}`;
}

class SimulatedDriver {
  constructor(id, initialLat, initialLon) {
    this.id = id;
    this.lat = initialLat;
    this.lon = initialLon;
    this.bearing = Math.random() * 360; // 0 to 360 degrees
    this.speed = 25 + Math.random() * 35; // 25 - 60 km/h
    this.token = generateDriverToken(id);
    this.ws = null;
    this.timer = null;
    this.connected = false;
  }

  start() {
    const wsUrl = `${TARGET_WS_URL}?token=${this.token}`;
    this.ws = new WebSocket(wsUrl);

    this.ws.on('open', () => {
      this.connected = true;
      // Stagger initial packet slightly to avoid burst stampede
      const jitter = Math.floor(Math.random() * UPDATE_INTERVAL_MS);
      setTimeout(() => {
        this.sendLocation();
        this.timer = setInterval(() => this.sendLocation(), UPDATE_INTERVAL_MS);
      }, jitter);
    });

    this.ws.on('message', () => {
      metrics.totalAcks++;
    });

    this.ws.on('close', () => {
      this.connected = false;
      this.cleanup();
    });

    this.ws.on('error', (err) => {
      this.connected = false;
      this.cleanup();
    });
  }

  sendLocation() {
    if (!this.connected || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return;
    }

    // Dead reckoning coordinate update
    const deltaHours = UPDATE_INTERVAL_MS / 3600000;
    const distanceKm = this.speed * deltaHours;

    // Small heading variation (+/- 15 deg) to simulate real traffic road turns
    this.bearing = (this.bearing + (Math.random() * 30 - 15) + 360) % 360;

    const radBearing = (this.bearing * Math.PI) / 180;
    const deltaLat = (distanceKm * Math.cos(radBearing)) / 111.0;
    const deltaLon = (distanceKm * Math.sin(radBearing)) / (111.0 * Math.cos((this.lat * Math.PI) / 180));

    this.lat += deltaLat;
    this.lon += deltaLon;

    const packet = JSON.stringify({
      latitude: parseFloat(this.lat.toFixed(6)),
      longitude: parseFloat(this.lon.toFixed(6)),
      bearing: parseFloat(this.bearing.toFixed(1)),
      speed: parseFloat(this.speed.toFixed(1)),
      status: 'AVAILABLE'
    });

    this.ws.send(packet);
    metrics.totalSent++;
    metrics.intervalSent++;
  }

  kill() {
    this.cleanup();
    if (this.ws) {
      try {
        this.ws.terminate();
      } catch (_) {}
    }
    this.connected = false;
  }

  cleanup() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}

const metrics = {
  totalSent: 0,
  intervalSent: 0,
  totalAcks: 0
};

console.log('==========================================================');
console.log(` 🚀 Starting Real-Time Driver Simulator`);
console.log(` Drivers: ${DRIVER_COUNT} | Target: ${TARGET_WS_URL}`);
console.log(` Update Rate: every ${UPDATE_INTERVAL_MS}ms per driver`);
console.log('==========================================================\n');

const drivers = [];

for (let i = 1; i <= DRIVER_COUNT; i++) {
  const driverId = `driver-${String(i).padStart(4, '0')}`;
  // Spread within ~10km radius of center (0.09 degrees approx 10km)
  const angle = Math.random() * 2 * Math.PI;
  const radius = Math.random() * 0.08;
  const lat = BASE_LAT + radius * Math.cos(angle);
  const lon = BASE_LON + radius * Math.sin(angle);

  const driver = new SimulatedDriver(driverId, lat, lon);
  drivers.push(driver);
}

// Stagger driver connections smoothly to avoid synthetic connection spikes
const connectionDelay = Math.max(12, Math.floor(6000 / DRIVER_COUNT));
drivers.forEach((driver, idx) => {
  setTimeout(() => driver.start(), idx * connectionDelay);
});

// Periodic throughput metrics
const statsInterval = setInterval(() => {
  const activeCount = drivers.filter((d) => d.connected).length;
  const updatesPerSec = (metrics.intervalSent / 2).toFixed(0);
  metrics.intervalSent = 0;

  console.log(
    `[Simulator] Active Drivers: ${activeCount}/${DRIVER_COUNT} | Ingestion Rate: ${updatesPerSec} updates/sec | Total Packets: ${metrics.totalSent} | Total Acks: ${metrics.totalAcks}`
  );
}, 2000);

// Optional: Simulate driver termination/kills to test TTL presence eviction
if (KILL_AFTER_SEC > 0) {
  setTimeout(() => {
    const toKillCount = Math.floor(drivers.length * KILL_RATIO);
    console.log(`\n⚠️  Simulating sudden crash/disconnect for ${toKillCount} drivers (to verify TTL eviction)...`);
    for (let i = 0; i < toKillCount; i++) {
      drivers[i].kill();
    }
    console.log(`✅ Terminated ${toKillCount} drivers. Expecting Redis TTL eviction within 10 seconds.\n`);
  }, KILL_AFTER_SEC * 1000);
}

if (DURATION_SEC > 0) {
  setTimeout(() => {
    console.log(`\n⏱️  Simulation duration (${DURATION_SEC}s) reached. Shutting down drivers...`);
    drivers.forEach((d) => d.kill());
    clearInterval(statsInterval);
    process.exit(0);
  }, DURATION_SEC * 1000);
}

process.on('SIGINT', () => {
  console.log('\nStopping simulator...');
  drivers.forEach((d) => d.kill());
  clearInterval(statsInterval);
  process.exit(0);
});
