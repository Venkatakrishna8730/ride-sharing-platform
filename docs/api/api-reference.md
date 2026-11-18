# API Reference: Stage 1 (Core Services & REST)

This document provides request/response contracts, schemas, headers, and `curl` commands for all Stage 1 endpoints.

---

## 1. Postman Import
You can import [`docs/api/postman_collection.json`](postman_collection.json) directly into Postman.
- **Variables defined:**
  - `{{baseUrl}}`: Default `http://localhost:8080` (API Gateway)
  - `{{accessToken}}`: Automatically captured when you execute the **Login Rider** request!
  - `{{tripId}}`: Automatically captured when you execute the **Create Ride Request**!

---

## 2. Authentication & Users

### 2.1 Register User
- **Endpoint:** `POST /api/v1/auth/register`
- **Auth:** Public
- **Request Body:**
```json
{
  "email": "rider1@example.com",
  "password": "SecurePass123!",
  "fullName": "Jane Rider",
  "phoneNumber": "+1234567890",
  "role": "RIDER"
}
```
*(Valid roles: `RIDER`, `DRIVER`, `ADMIN`)*
- **Response (201 Created):**
```json
{
  "id": "e70d49ea-800a-42c2-b5f7-64259b3fa455",
  "email": "rider1@example.com",
  "fullName": "Jane Rider",
  "phoneNumber": "+1234567890",
  "role": "RIDER",
  "status": "ACTIVE",
  "createdAt": "2026-10-31T10:00:00Z"
}
```

### 2.2 Login User
- **Endpoint:** `POST /api/v1/auth/login`
- **Auth:** Public
- **Request Body:**
```json
{
  "email": "rider1@example.com",
  "password": "SecurePass123!"
}
```
- **Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": "e70d49ea-800a-42c2-b5f7-64259b3fa455",
    "email": "rider1@example.com",
    "fullName": "Jane Rider",
    "phoneNumber": "+1234567890",
    "role": "RIDER",
    "status": "ACTIVE",
    "createdAt": "2026-10-31T10:00:00Z"
  }
}
```

### 2.3 Get User Profile
- **Endpoint:** `GET /api/v1/users/me`
- **Header:** `Authorization: Bearer <accessToken>`
- **Response (200 OK):** User object matching registration response.

---

## 3. Trips & Ride Lifecycle

### 3.1 Create Ride Request
- **Endpoint:** `POST /api/v1/trips`
- **Header:** `Authorization: Bearer <accessToken>`
- **Request Body:**
```json
{
  "pickupLatitude": 37.7749,
  "pickupLongitude": -122.4194,
  "pickupAddress": "Market St, San Francisco, CA",
  "dropoffLatitude": 37.7891,
  "dropoffLongitude": -122.4014,
  "dropoffAddress": "Financial District, San Francisco, CA",
  "currency": "USD"
}
```
- **Response (201 Created):**
```json
{
  "id": "c1387693-5ba2-4043-85f8-d42bcda5672e",
  "riderId": "e70d49ea-800a-42c2-b5f7-64259b3fa455",
  "driverId": null,
  "status": "REQUESTED",
  "pickupLatitude": 37.7749,
  "pickupLongitude": -122.4194,
  "pickupAddress": "Market St, San Francisco, CA",
  "dropoffLatitude": 37.7891,
  "dropoffLongitude": -122.4014,
  "dropoffAddress": "Financial District, San Francisco, CA",
  "distanceKm": 2.21,
  "fareEstimate": 6.37,
  "actualFare": null,
  "currency": "USD",
  "cancellationReason": null,
  "cancelledBy": null,
  "createdAt": "2026-10-31T10:05:00Z",
  "updatedAt": "2026-10-31T10:05:00Z"
}
```

### 3.2 Get Trip by ID
- **Endpoint:** `GET /api/v1/trips/{id}`
- **Header:** `Authorization: Bearer <accessToken>`
- **Response (200 OK):** Trip entity JSON.

### 3.3 Advance State Machine
- **Endpoint:** `PATCH /api/v1/trips/{id}/status`
- **Header:** `Authorization: Bearer <accessToken>`

#### Valid Transitions Matrix:
1. `REQUESTED` $\rightarrow$ `MATCHING`
```json
{ "targetStatus": "MATCHING" }
```
2. `MATCHING` $\rightarrow$ `ACCEPTED` *(Requires driverId)*
```json
{ "targetStatus": "ACCEPTED", "driverId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11" }
```
3. `ACCEPTED` $\rightarrow$ `ARRIVING`
```json
{ "targetStatus": "ARRIVING" }
```
4. `ARRIVING` $\rightarrow$ `IN_PROGRESS`
```json
{ "targetStatus": "IN_PROGRESS" }
```
5. `IN_PROGRESS` $\rightarrow$ `COMPLETED`
```json
{ "targetStatus": "COMPLETED" }
```
6. Any non-completed state $\rightarrow$ `CANCELLED`
```json
{ "targetStatus": "CANCELLED", "cancellationReason": "Rider requested cancellation" }
```

#### Invalid Transition Response (409 Conflict):
Attempting an invalid transition (e.g. `REQUESTED` directly to `COMPLETED`) produces:
```json
{
  "status": 409,
  "error": "State Transition Conflict",
  "message": "Invalid state transition: Cannot perform action 'COMPLETE' when trip is in state 'REQUESTED'",
  "path": "/api/v1/trips/c1387693-5ba2-4043-85f8-d42bcda5672e/status",
  "timestamp": "2026-10-31T10:06:00Z"
}
```

### 3.4 Get Rider Trip History
- **Endpoint:** `GET /api/v1/trips/rider?page=0&size=10`
- **Header:** `Authorization: Bearer <accessToken>`
- **Response (200 OK):** Paginated array of trips ordered by `createdAt DESC`.

---

## 4. Driver Location & Real-Time Telemetry (Stage 2)

### 4.1 Query Nearby Drivers
- **Endpoint:** `GET /api/v1/drivers/nearby?latitude=12.9716&longitude=77.5946&radiusKm=10&limit=50`
- **Auth:** Public
- **Query Parameters:**
  - `latitude` (required, float): Center latitude $[-90, 90]$
  - `longitude` (required, float): Center longitude $[-180, 180]$
  - `radiusKm` (optional, float, default `5`): Search radius in kilometers
  - `limit` (optional, integer, default `20`): Maximum drivers to return
- **Response (200 OK):**
```json
{
  "center": {
    "latitude": 12.9716,
    "longitude": 77.5946
  },
  "radiusKm": 10,
  "count": 2,
  "queryExecutionTimeMs": 2.14,
  "drivers": [
    {
      "driverId": "driver-0042",
      "distanceKm": 1.45,
      "longitude": 77.5892,
      "latitude": 12.9681,
      "bearing": 182.4,
      "speed": 38.5,
      "status": "AVAILABLE",
      "lastSeenAt": 1794750431000
    }
  ]
}
```

### 4.2 Query Single Driver Location & Presence
- **Endpoint:** `GET /api/v1/drivers/{driverId}/location`
- **Auth:** Public
- **Response (200 OK):**
```json
{
  "driverId": "driver-0042",
  "longitude": 77.5892,
  "latitude": 12.9681,
  "bearing": 182.4,
  "speed": 38.5,
  "status": "AVAILABLE",
  "isOnline": true,
  "lastSeenAt": 1794750431000
}
```
- **Response (404 Not Found - Driver Disconnected or TTL Expired):**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Driver driver-0042 is offline or not found"
}
```

### 4.3 WebSocket Driver Location Ingestion
- **Protocol:** `WebSocket`
- **URL:** `ws://localhost:8080/ws/driver/location?token=<jwtAccessToken>`
- **Handshake Auth:** Driver JWT required in query parameter `?token=...`
- **Ingestion Packet (Client -> Server):**
```json
{
  "latitude": 12.9716,
  "longitude": 77.5946,
  "bearing": 94.5,
  "speed": 42.0,
  "status": "AVAILABLE"
}
```
- **Server Acknowledgment (Server -> Client):**
```json
{
  "ack": true,
  "timestamp": 1794750431050
}
```

### 4.4 Real-Time Moving Fleet Map
- **URL:** `http://localhost:8080/map`
- **Description:** Interactive Leaflet / OpenStreetMap web page displaying active driver pins moving in real time across coordinates, polling nearby drivers with live telemetry metrics.

