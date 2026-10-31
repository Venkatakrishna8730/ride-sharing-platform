# Flagship Ride-Sharing Platform

A polyglot microservices ride-hailing backend built in 9 shippable stages, designed to demonstrate distributed systems principles on a developer laptop and local Kubernetes cluster.

---

## Architecture Overview

- **Polyglot Microservices:** Spring Boot (Java 17/21) for transactional domains and Node.js for high-concurrency connection-oriented domains.
- **Datastores:** PostgreSQL 16 (Relational/Outbox), Redis 7.2 (Geospatial/TTL/Locks), Apache Kafka 3.8 (KRaft mode).
- **Communication:** REST (External API Gateway), gRPC (Internal low-latency RPC), WebSockets (Driver location & push notifications), Kafka (Event streaming).

Detailed architecture documentation, capacity estimations, and state machine diagrams are located in [`docs/design.md`](docs/design.md).

---

## Repository Layout

```
ride-platform/
├── services/
│   ├── gateway/            # Node.js API Gateway (Routing, Rate-limiting, JWT)
│   ├── user-auth/          # Spring Boot User/Auth Service
│   ├── trip/               # Spring Boot Trip Lifecycle Service
│   ├── matching/           # Spring Boot Matching Service
│   ├── pricing/            # Spring Boot Dynamic Pricing Service
│   ├── payment/            # Spring Boot Mock Payment & Saga Service
│   ├── driver-location/    # Node.js WebSocket GPS Location Ingestion Service
│   ├── notification/       # Node.js WebSocket Push Notification Service
│   └── graphql-bff/        # Node.js Apollo GraphQL BFF Service
├── web/                    # React Web Application (Rider, Driver, Admin)
├── proto/                  # Shared Protobuf specifications for gRPC
├── deploy/
│   ├── compose/            # Docker Compose configurations
│   └── k8s/                # Kubernetes manifests
├── loadtest/               # k6 load testing scripts
├── chaos/                  # Fault-injection and chaos scripts
└── docs/
    ├── design.md           # System design, capacity estimation, and state machines
    └── adr/                # Architecture Decision Records
```

---

## Quickstart (Stage 0 Infrastructure)

### 1. Prerequisites
- Docker Engine $\ge 24.0$ & Docker Compose v2
- Java 17 or 21
- Node.js $\ge 20$

### 2. Start Core Infrastructure
```bash
docker compose up -d
```

This starts:
- **PostgreSQL 16:** `localhost:5433` (mapped from container `5432`, `postgres` / `postgrespassword`)
- **Redis 7.2:** `localhost:6379`
- **Apache Kafka 3.8 (KRaft):** `localhost:9092`

Verify service health:
```bash
docker compose ps
```
