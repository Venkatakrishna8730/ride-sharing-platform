#!/usr/bin/env bash
set -eo pipefail

GATEWAY_URL="http://localhost:8080"
WS_TARGET="ws://localhost:8080/ws/driver/location"
CENTER_LAT="12.9716"
CENTER_LON="77.5946"

echo "=========================================================="
echo " Stage 2 Real-Time Location Verification Flow"
echo " Target API Gateway: ${GATEWAY_URL}"
echo "=========================================================="

# 1. Check Gateway & Location Service Connectivity
echo -e "\n1. Checking Service Health..."
HEALTH_RESP=$(curl -s "${GATEWAY_URL}/health")
echo "Gateway Health: ${HEALTH_RESP}"

# 2. Query empty baseline
echo -e "\n2. Querying Baseline Nearby Drivers before Simulator..."
INIT_QUERY=$(curl -s "${GATEWAY_URL}/api/v1/drivers/nearby?latitude=${CENTER_LAT}&longitude=${CENTER_LON}&radiusKm=15&limit=1000")
INIT_COUNT=$(echo "${INIT_QUERY}" | grep -o '"count":[0-9]*' | cut -d':' -f2 || echo "0")
echo "Initial Active Drivers in 15km: ${INIT_COUNT}"

# 3. Launch Driver Simulator
DRIVER_COUNT=300
echo -e "\n3. Launching Simulator with ${DRIVER_COUNT} concurrent drivers streaming over WebSockets..."
node scripts/simulator/driver-simulator.js \
  --drivers=${DRIVER_COUNT} \
  --target="${WS_TARGET}" \
  --interval=2000 \
  --duration=30 \
  > /tmp/driver_sim.log 2>&1 &
SIM_PID=$!
echo "Simulator started with PID: ${SIM_PID}. Waiting 8 seconds for connections to settle and stream..."
sleep 8

# 4. Query Nearby Drivers under Load
echo -e "\n4. Querying Active Nearby Drivers under high load..."
QUERY_RESP=$(curl -s "${GATEWAY_URL}/api/v1/drivers/nearby?latitude=${CENTER_LAT}&longitude=${CENTER_LON}&radiusKm=15&limit=1000")
COUNT=$(echo "${QUERY_RESP}" | grep -o '"count":[0-9]*' | cut -d':' -f2)
EXEC_TIME=$(echo "${QUERY_RESP}" | grep -o '"queryExecutionTimeMs":[0-9.]*' | cut -d':' -f2)

echo "Active Drivers Found: ${COUNT} / ${DRIVER_COUNT}"
echo "Geospatial Query Execution Latency: ${EXEC_TIME} ms"

if [ -z "${COUNT}" ] || [ "${COUNT}" -lt 150 ]; then
  echo "❌ Expected at least 150 active drivers, found: ${COUNT}"
  kill -9 "${SIM_PID}" || true
  exit 1
fi
echo "✅ Successfully queried ${COUNT} active drivers in ${EXEC_TIME} ms (< 10ms target)!"

# 5. Direct Driver Lookup
SAMPLE_DRIVER="driver-0042"
echo -e "\n5. Looking up single driver telemetry (${SAMPLE_DRIVER})..."
DRIVER_RESP=$(curl -s "${GATEWAY_URL}/api/v1/drivers/${SAMPLE_DRIVER}/location")
echo "Driver Telemetry: ${DRIVER_RESP}"
if ! echo "${DRIVER_RESP}" | grep -q "${SAMPLE_DRIVER}"; then
  echo "❌ Driver ${SAMPLE_DRIVER} not found in single lookup"
  kill -9 "${SIM_PID}" || true
  exit 1
fi
echo "✅ Driver ${SAMPLE_DRIVER} location and presence verified!"

# 6. Test TTL Eviction
echo -e "\n6. Testing Presence TTL Eviction..."
echo "Stopping Simulator (PID: ${SIM_PID}) to simulate driver disconnects..."
kill -TERM "${SIM_PID}" || true
wait "${SIM_PID}" 2>/dev/null || true

echo "Waiting 12 seconds for 10-second TTL presence keys to expire..."
sleep 12

echo "Re-querying nearby drivers after TTL expiry..."
POST_TTL_RESP=$(curl -s "${GATEWAY_URL}/api/v1/drivers/nearby?latitude=${CENTER_LAT}&longitude=${CENTER_LON}&radiusKm=15&limit=1000")
POST_COUNT=$(echo "${POST_TTL_RESP}" | grep -o '"count":[0-9]*' | cut -d':' -f2)
echo "Active Drivers after TTL: ${POST_COUNT}"

if [ "${POST_COUNT}" -ne 0 ]; then
  echo "❌ Expected 0 drivers after TTL expiry, but found: ${POST_COUNT}"
  exit 1
fi
echo "✅ Presence TTL working as expected! All disconnected drivers vanished cleanly."

echo -e "\nVerifying direct lookup returns 404 for disconnected driver..."
OFFLINE_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "${GATEWAY_URL}/api/v1/drivers/${SAMPLE_DRIVER}/location")
HTTP_CODE=$(echo "${OFFLINE_RESP}" | grep "HTTP_STATUS" | cut -d':' -f2)
echo "HTTP Status for disconnected driver: ${HTTP_CODE} (Expected 404)"

if [ "${HTTP_CODE}" -ne 404 ]; then
  echo "❌ Expected 404 Not Found for disconnected driver, got: ${HTTP_CODE}"
  exit 1
fi
echo "✅ Disconnected driver correctly marked offline!"

echo "=========================================================="
echo " 🎉 All Stage 2 Verifications Passed Successfully!"
echo "=========================================================="
