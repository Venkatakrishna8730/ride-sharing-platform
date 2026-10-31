# Ride-Sharing Platform: System Design Document

**Author:** Venkatakrishna  
**Status:** In Progress (Stage 0: Foundations & Design)  
**Target Environment:** Local Monorepo (Docker Compose & Minikube)

---

## 1. Problem Statement & Project Goal

The goal of this project is to build an end-to-end, polyglot microservices ride-sharing platform (Spring Boot + Node.js) that demonstrates practical system design concepts directly on a developer workstation. 

Rather than focusing on surface-level UI or commercial third-party integrations, the architecture is engineered to tackle core distributed systems challenges:
- High-throughput geospatial location streaming
- Atomic concurrency control and prevention of double-assignment
- Reliable messaging via the Transactional Outbox pattern
- Graceful degradation, distributed transactions (Saga), and resilience under failure
- Cache stampede prevention and replication lag mitigation

---

## 2. Functional Requirements (FR)

1. **User Authentication & Role Management:**
   - Users can register and log in via JWT authentication.
   - Distinct roles are supported: `RIDER`, `DRIVER`, and `ADMIN`.
   - The API Gateway authenticates requests and forwards user context (`X-User-Id`, `X-User-Role`) to internal services.

2. **Driver Availability & Location Streaming:**
   - Drivers can toggle availability status (`ONLINE` / `OFFLINE`).
   - Online drivers stream live GPS coordinates every 3–5 seconds over a persistent WebSocket connection.
   - Driver presence is tracked with a TTL so inactive/disconnected drivers automatically expire.

3. **Nearby Driver Discovery:**
   - Riders can view real-time locations of available drivers within a configurable radius (e.g., $3\text{ km}$) on a map.

4. **Fare Estimation:**
   - Riders receive an upfront fare estimate based on distance, duration, and dynamic surge pricing.

5. **Ride Request Initiation:**
   - Riders can request a ride specifying pickup and drop-off coordinates.

6. **Atomic Driver Matching & Dispatch:**
   - The matching engine queries candidate drivers, ranks them by distance, and atomically claims an available driver.
   - Guarantee: A driver must **never** be assigned to more than one ride at a time under concurrent load.

7. **Ride Offer Lifecycle:**
   - Drivers receive an offer with an acceptance deadline (e.g., 15 seconds).
   - If the driver declines or times out, the system automatically tries the next nearest driver.

8. **Live Trip Tracking:**
   - Riders receive real-time location updates of their assigned driver moving toward pickup and destination over WebSockets.

9. **Trip Lifecycle State Machine:**
   - Strict state transitions:
     $$\text{REQUESTED} \longrightarrow \text{MATCHING} \longrightarrow \text{ACCEPTED} \longrightarrow \text{ARRIVING} \longrightarrow \text{IN\_PROGRESS} \longrightarrow \text{COMPLETED}$$
   - Cancellation paths: `CANCELLED_BY_RIDER`, `CANCELLED_BY_DRIVER`, `NO_DRIVERS_AVAILABLE`.

10. **Payment & Receipt Generation:**
    - On trip completion, payment is processed through a distributed transaction (Saga pattern with compensating transactions).
    - Idempotency keys prevent duplicate charges on network retries.
    - Receipts are generated and made available to riders.

11. **Trip History & Analytics:**
    - Riders and drivers can view paginated trip history and receipts.
    - GraphQL BFF provides aggregated views (earnings, completed trips) with batching to avoid the N+1 problem.

---

## 3. Non-Functional Requirements (NFR)

1. **Latency Targets (p95):**
   - Driver location ingestion: $< 50\text{ ms}$.
   - Nearby driver geospatial query: $< 20\text{ ms}$.
   - Trip creation & state mutation API: $< 100\text{ ms}$.

2. **Concurrency & Correctness:**
   - **Zero Double-Booking:** Strict mutual exclusion on driver assignment even under 100+ concurrent requests competing for the same driver.
   - **State Machine Integrity:** Disallow invalid or out-of-order state transitions.

3. **Consistency vs. Availability (CAP Trade-offs):**
   - **CP (Consistency / Partition Tolerance):** Matching engine, trip state mutations, and payment transactions require strong consistency.
   - **AP (Availability / Partition Tolerance):** Driver location updates, map pin visualizations, and read-model analytics prioritize high throughput and availability over instant global consistency.

4. **Reliability & Zero Data Loss:**
   - **Transactional Outbox:** Ride lifecycle events are committed to PostgreSQL outbox tables within the same transaction as state changes before publishing to Kafka.
   - **Idempotent Consumers:** All event consumers deduplicate messages using unique event/trip IDs to safely handle at-least-once delivery.

5. **Fault Tolerance & Resilience:**
   - Fallbacks and circuit breakers (Resilience4j) ensure system degrades gracefully when downstream services (e.g., Pricing) are unavailable.
   - Expired driver sessions vanish cleanly via Redis key TTL without manual cleanup.

6. **Local Scalability Targets:**
   - Must run entirely on a standard developer laptop using Docker Compose (and Minikube in later stages).
   - Capable of ingesting updates from 500–1,000 simulated drivers concurrently.
   - Stable memory footprint managed via container resource limits.

---

## 4. Scope Guard (Out of Scope / Non-Goals)

1. **No Paid Maps API:** No Google Maps or Mapbox APIs. We use OpenStreetMap tiles with Leaflet and synthetic/haversine coordinate math.
2. **No Real Payment Gateways:** No Stripe, PayPal, or banking integrations. A mock payment service simulates network latency and payment failure scenarios.
3. **No Native Mobile Apps:** The user interface is a responsive React web application supporting multi-tab testing (Rider, Driver, and Admin).
4. **No Real Cloud Spend:** Cloud infrastructure (AWS EKS, RDS, ElastiCache, MSK) is documented as an architecture design only. All runnable stages execute locally.

---

## 5. Back-of-the-Envelope Capacity Estimations

### 5.1 Production Target (Single Metropolitan City Scale)
- **Active User Base:** 100,000 Daily Active Riders (DAR), 10,000 Daily Active Drivers (DAD).
- **Trips per Day:** 100,000 completed trips/day.
- **Trip QPS:**
  $$\text{Average Trip QPS} = \frac{100,000 \text{ trips}}{12 \times 3,600 \text{ s}} \approx 2.3 \text{ requests/sec}$$
  $$\text{Peak Trip QPS (2-3x average)} \approx 10 \text{ to } 15 \text{ requests/sec}$$
- **Driver Location Updates (High-Write Workload):**
  - 10,000 online drivers broadcasting GPS coordinates every 4 seconds.
  $$\text{Location Ingestion QPS} = \frac{10,000 \text{ drivers}}{4 \text{ s}} = 2,500 \text{ updates/sec}$$
  $$\text{Peak Location Ingestion QPS} \approx 5,000 \text{ updates/sec}$$
  - Payload per update: `driver_id` (UUID - 16 bytes), `lat` (8 bytes), `lng` (8 bytes), `heading` (4 bytes), `timestamp` (8 bytes) + JSON envelope $\approx 100 \text{ bytes}$.
  $$\text{Network Ingress Bandwidth} = 2,500 \times 100 \text{ B} = 250 \text{ KB/sec} \ (\approx 2 \text{ Mbps})$$
- **Nearby Driver Discovery Queries (High-Read Workload):**
  - 20,000 concurrent active riders viewing map pins (polling/streaming every 5 seconds).
  $$\text{Nearby Query QPS} = \frac{20,000}{5 \text{ s}} = 4,000 \text{ queries/sec}$$
- **Storage Projections (Per Year):**
  - Trips: $100,000 \text{ trips/day} \times 365 = 36.5\text{M trips/year}$.
  - At $1 \text{ KB}$ per trip record (with pickup/drop addresses, fare, timeline metadata):
    $$\text{Trip Storage} = 36.5\text{M} \times 1 \text{ KB} \approx 36.5 \text{ GB/year}$$
  - Driver Location Telemetry:
    $$2,500 \text{ updates/s} \times 86,400 \text{ s/day} \times 365 \text{ days} \times 100 \text{ bytes} \approx 7.88 \text{ TB/year}$$
    *(Design takeaway: Raw telemetry must not be stored long-term in PostgreSQL; keep only latest position in Redis with TTL and downsample archival telemetry to cold object storage / Kafka).*

### 5.2 Local Developer Simulation Target (Laptop Benchmark)
- **Simulated Drivers:** 500 to 1,000 active concurrent drivers.
- **Location Stream Ingestion:** $\approx 250 \text{ to } 333 \text{ updates/sec}$ into Redis GEO.
- **Concurrent Trip Requests:** 100 simultaneous booking requests to test lock contention.
- **Hardware Budget:** $\le 4 \text{ GB}$ total RAM allocated across all local Docker containers.

---

## 6. Service Boundaries & Data Ownership

| Service Name | Stack | Primary Responsibility | Data Store Owned | Communication Style |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | Node.js (Fastify/Express) | Routing, JWT validation, rate limiting, request logging | Redis (rate-limit counters) | Inbound REST / Outbound REST |
| **User/Auth Service** | Java (Spring Boot) | User registration, login, JWT issuance, profile management | PostgreSQL (`users`, `profiles`) | REST (from Gateway) |
| **Trip Service** | Java (Spring Boot) | Trip lifecycle FSM, trip history, outbox event generation | PostgreSQL (`trips`, `trip_outbox`) | REST, Kafka Producer |
| **Driver-Location** | Node.js (`ws`) | Accepts WebSocket GPS streams, tracks presence, serves nearby queries | Redis (`GEO`, presence TTL) | WebSocket (client), gRPC (server) |
| **Matching Service** | Java (Spring Boot) | Finds candidate drivers, atomic driver claiming, offer timeouts | Redis (distributed locks) | gRPC client, Kafka Consumer |
| **Pricing Service** | Java (Spring Boot) | Dynamic fare estimate, surge multiplier calculation | Redis (surge cache) | gRPC (server) |
| **Payment Service** | Java (Spring Boot) | Mock payment processing, Saga compensation, idempotency | PostgreSQL (`payments`, `receipts`) | gRPC, Kafka Consumer |
| **Notification** | Node.js | Consumes ride events from Kafka and pushes real-time WebSocket updates | Redis (connection registry) | Kafka Consumer, WebSocket |
| **GraphQL BFF** | Node.js (Apollo) | Aggregated queries for trip history, driver earnings; DataLoader | Reads CQRS read model | GraphQL |

---

## 7. High-Level Architecture Diagram

```mermaid
flowchart TD
    subgraph Clients["Clients"]
        RiderClient["Rider (React Web / Browser)"]
        DriverClient["Driver (React Web / Simulator)"]
    end

    subgraph Edge["Edge Layer"]
        Gateway["API Gateway (Node.js :8080)\n[Rate Limiting, JWT Validation]"]
    end

    subgraph RealTime["Real-Time Location & Telemetry"]
        DriverLoc["Driver-Location Service (Node.js :8081)\n[WebSocket GPS Ingestion]"]
        RedisGeo[("Redis 7\n(GEOADD, GEOSEARCH, TTL Presence)")]
    end

    subgraph CoreServices["Core Business Microservices"]
        AuthSvc["User/Auth Service\n(Spring Boot :8082)"]
        TripSvc["Trip Service\n(Spring Boot :8083)\n[State Machine & Outbox]"]
        MatchSvc["Matching Service\n(Spring Boot :8084)\n[Atomic Claiming]"]
        PricingSvc["Pricing Service\n(Spring Boot :8085)\n[Surge & Fare]"]
        PaymentSvc["Payment Service\n(Spring Boot :8086)\n[Saga Participant]"]
    end

    subgraph DataLayer["Datastores"]
        PostgresDB[("PostgreSQL 16\n(Users, Trips, Payments, Outbox)")]
        RedisLock[("Redis 7\n(Distributed Locks)")]
        KafkaBroker["Apache Kafka (KRaft mode :9092)\n[Topics: ride.events, driver.location]"]
    end

    subgraph AsyncDownstream["Async & Read Optimization"]
        NotifSvc["Notification Service (Node.js)\n[Push over WebSocket]"]
        GraphqlBFF["GraphQL BFF (Node.js :4000)\n[DataLoader Batching]"]
    end

    %% Client Interactions
    RiderClient -->|HTTP REST| Gateway
    RiderClient <-->|WebSocket Notifications| NotifSvc
    DriverClient <-->|WebSocket GPS Stream| DriverLoc
    DriverClient <-->|WebSocket Ride Offers| NotifSvc

    %% Gateway Routing
    Gateway -->|Forward /auth| AuthSvc
    Gateway -->|Forward /trips| TripSvc

    %% Internal Communication
    DriverLoc <--> RedisGeo
    AuthSvc <--> PostgresDB
    TripSvc <--> PostgresDB
    TripSvc -->|Outbox Relay| KafkaBroker

    TripSvc -->|gRPC /match| MatchSvc
    MatchSvc -->|gRPC /nearby| DriverLoc
    MatchSvc -->|gRPC /fare| PricingSvc
    MatchSvc <-->|SET NX PX (Atomic Claim)| RedisLock
    MatchSvc -->|Publish Offer| KafkaBroker

    KafkaBroker --> NotifSvc
    KafkaBroker --> PaymentSvc
    KafkaBroker --> GraphqlBFF
    PaymentSvc <--> PostgresDB
```

---

## 8. Ride State Machine

```
                    +-------------------+
                    |     REQUESTED     |
                    +---------+---------+
                              |
                              v
                    +-------------------+       Driver Timeout / Decline
                    |     MATCHING      +------------------------------+
                    +---------+---------+                              |
                              | Driver Accepts                         v
                              v                              +----------------------+
                    +-------------------+                    | NO_DRIVERS_AVAILABLE |
                    +     ACCEPTED      |                    +----------------------+
                    +---------+---------+
                              |
                              v
                    +-------------------+
                    |     ARRIVING      |
                    +---------+---------+
                              |
                              v
                    +-------------------+
                    |    IN_PROGRESS    |
                    +---------+---------+
                              |
                              v
                    +-------------------+
                    |     COMPLETED     |
                    +-------------------+

* Cancellations by rider or driver are permitted from REQUESTED, MATCHING, ACCEPTED, and ARRIVING states.
```


