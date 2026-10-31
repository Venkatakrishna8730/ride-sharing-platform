#!/usr/bin/env bash
set -e

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TIMESTAMP=$(date +%s)
EMAIL="rider_${TIMESTAMP}@example.com"
PASSWORD="SecurePass123!"

echo "=========================================================="
echo " Stage 1 End-to-End Verification Flow"
echo " Target API Gateway: ${GATEWAY_URL}"
echo "=========================================================="

echo -e "\n1. Checking API Gateway Health..."
HEALTH_RESP=$(curl -s -w "\n%{http_code}" "${GATEWAY_URL}/health")
HTTP_CODE=$(echo "$HEALTH_RESP" | tail -n1)
BODY=$(echo "$HEALTH_RESP" | head -n -1)
echo "HTTP Status: ${HTTP_CODE}, Response: ${BODY}"
if [ "$HTTP_CODE" -ne 200 ]; then
  echo "❌ Gateway is not healthy!"
  exit 1
fi

echo -e "\n2. Registering new Rider (${EMAIL})..."
REGISTER_PAYLOAD=$(cat <<EOF
{
  "email": "${EMAIL}",
  "password": "${PASSWORD}",
  "fullName": "Jane Verification",
  "phoneNumber": "+1234567890",
  "role": "RIDER"
}
EOF
)
REG_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GATEWAY_URL}/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "${REGISTER_PAYLOAD}")
HTTP_CODE=$(echo "$REG_RESP" | tail -n1)
BODY=$(echo "$REG_RESP" | head -n -1)
echo "HTTP Status: ${HTTP_CODE}, Body: ${BODY}"

echo -e "\n3. Logging in to obtain JWT Access Token..."
LOGIN_PAYLOAD=$(cat <<EOF
{
  "email": "${EMAIL}",
  "password": "${PASSWORD}"
}
EOF
)
LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GATEWAY_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "${LOGIN_PAYLOAD}")
HTTP_CODE=$(echo "$LOGIN_RESP" | tail -n1)
BODY=$(echo "$LOGIN_RESP" | head -n -1)
TOKEN=$(echo "$BODY" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ Failed to obtain accessToken from login response!"
  exit 1
fi
echo "✅ Authenticated! Token: ${TOKEN:0:20}..."

echo -e "\n4. Calling Protected Profile Endpoint (GET /api/v1/users/me)..."
ME_RESP=$(curl -s -w "\n%{http_code}" "${GATEWAY_URL}/api/v1/users/me" \
  -H "Authorization: Bearer ${TOKEN}")
HTTP_CODE=$(echo "$ME_RESP" | tail -n1)
BODY=$(echo "$ME_RESP" | head -n -1)
echo "HTTP Status: ${HTTP_CODE}, Profile: ${BODY}"

echo -e "\n5. Creating Ride Request (POST /api/v1/trips)..."
TRIP_PAYLOAD=$(cat <<EOF
{
  "pickupLatitude": 37.7749,
  "pickupLongitude": -122.4194,
  "pickupAddress": "Market Street, SF",
  "dropoffLatitude": 37.7891,
  "dropoffLongitude": -122.4014,
  "dropoffAddress": "Financial District, SF"
}
EOF
)
TRIP_RESP=$(curl -s -w "\n%{http_code}" -X POST "${GATEWAY_URL}/api/v1/trips" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${TRIP_PAYLOAD}")
HTTP_CODE=$(echo "$TRIP_RESP" | tail -n1)
BODY=$(echo "$TRIP_RESP" | head -n -1)
TRIP_ID=$(echo "$BODY" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
TRIP_STATUS=$(echo "$BODY" | grep -o '"status":"[^"]*' | cut -d'"' -f4)
echo "HTTP Status: ${HTTP_CODE}, Created Trip ID: ${TRIP_ID}, Status: ${TRIP_STATUS}"

if [ "$TRIP_STATUS" != "REQUESTED" ]; then
  echo "❌ Expected trip status REQUESTED, got: ${TRIP_STATUS}"
  exit 1
fi

echo -e "\n6. Testing State Machine Guard (Attempting illegal REQUESTED -> COMPLETED jump)..."
ILLEGAL_PAYLOAD='{"targetStatus":"COMPLETED"}'
ILLEGAL_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "${GATEWAY_URL}/api/v1/trips/${TRIP_ID}/status" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${ILLEGAL_PAYLOAD}")
HTTP_CODE=$(echo "$ILLEGAL_RESP" | tail -n1)
BODY=$(echo "$ILLEGAL_RESP" | head -n -1)
echo "HTTP Status: ${HTTP_CODE} (Expected 409 Conflict)"
echo "Response: ${BODY}"

if [ "$HTTP_CODE" -ne 409 ]; then
  echo "❌ Expected 409 Conflict for illegal state transition, got: ${HTTP_CODE}"
  exit 1
fi
echo "✅ State Machine correctly blocked illegal transition!"

echo -e "\n7. Valid State Transition (REQUESTED -> CANCELLED)..."
CANCEL_PAYLOAD='{"targetStatus":"CANCELLED","cancellationReason":"Test cancellation"}'
CANCEL_RESP=$(curl -s -w "\n%{http_code}" -X PATCH "${GATEWAY_URL}/api/v1/trips/${TRIP_ID}/status" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${CANCEL_PAYLOAD}")
HTTP_CODE=$(echo "$CANCEL_RESP" | tail -n1)
BODY=$(echo "$CANCEL_RESP" | head -n -1)
NEW_STATUS=$(echo "$BODY" | grep -o '"status":"[^"]*' | cut -d'"' -f4)
echo "HTTP Status: ${HTTP_CODE}, New Status: ${NEW_STATUS}"

if [ "$NEW_STATUS" != "CANCELLED" ]; then
  echo "❌ Expected status CANCELLED, got: ${NEW_STATUS}"
  exit 1
fi

echo -e "\n=========================================================="
echo " 🎉 All Stage 1 End-to-End Verifications Succeeded!"
echo "=========================================================="
