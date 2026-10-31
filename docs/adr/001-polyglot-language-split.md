# ADR 001: Polyglot Architecture & Language Split

## Status
Accepted

## Context
A ride-sharing system has fundamentally two distinct kinds of workloads:
1. **Transaction & Business Logic Heavy:** User accounts, trip lifecycle state machines, atomic driver matching, fare calculation, and payments. These demand strong typing, strict transactional guarantees (ACID), and robust concurrency primitives.
2. **Connection & I/O Heavy:** Ingesting GPS telemetry streams over thousands of concurrent WebSockets, fan-out push notifications, API gateway reverse-proxying, and GraphQL schema aggregation.

## Decision
We adopt a **polyglot microservices architecture**:
- **Java (Spring Boot):** `user-auth`, `trip`, `matching`, `pricing`, `payment`. Matches enterprise microservice standards, provides type safety, mature transaction management, and robust concurrency control.
- **Node.js (TypeScript / Fastify / Express):** `gateway`, `driver-location`, `notification`, `graphql-bff`. Utilizes the non-blocking V8 event loop for high-density persistent WebSocket connections and low-overhead HTTP proxying.

## Trade-offs & Mitigations
- *Trade-off:* Two distinct toolchains and dependency ecosystems.
- *Mitigation:* Unified protocol definitions (`proto/` directory for gRPC), shared JSON structured logging schemas, and consistent distributed trace headers (`traceparent`).
