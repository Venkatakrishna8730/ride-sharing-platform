# Flagship Ride-Sharing Platform

A polyglot microservices ride-hailing backend engineered with production-grade patterns, designed to demonstrate distributed systems principles on a Kubernetes cluster.

---

## Architecture Overview

- **Polyglot Microservices:** Spring Boot (Java 17/21) for transactional business domains and Node.js (TypeScript) for high-concurrency connection-oriented domains.
- **Datastores:** PostgreSQL 16 (Relational & Outbox tables), Redis 7.2 (Geospatial indexing, TTL presence, and atomic locks), Apache Kafka 3.8 (KRaft mode).
- **Communication:** REST (External API Gateway), gRPC (Internal low-latency RPC), WebSockets (Driver telemetry & push notifications), Kafka (Event streaming).
- **Orchestration:** Production-grade Kubernetes manifests structured with Kustomize.

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
│   └── k8s/                # Kubernetes manifests & Kustomize overlays
│       ├── namespace.yaml
│       ├── kustomization.yaml
│       └── infrastructure/
│           ├── postgres/   # PostgreSQL 16 StatefulSet & Config
│           ├── redis/      # Redis 7.2 StatefulSet & Config
│           └── kafka/      # Kafka 3.8 (KRaft) StatefulSet & Config
├── loadtest/               # k6 load testing scripts
├── chaos/                  # Fault-injection and chaos scripts
└── docs/
    ├── design.md           # System design, capacity estimation, and state machines
    └── adr/                # Architecture Decision Records
```

---

## Quickstart (Infrastructure Deployment)

### 1. Prerequisites
- `kubectl` $\ge v1.28$
- Kubernetes cluster (e.g. Minikube, Kind, or remote cluster)
- Java 17 or 21
- Node.js $\ge 20$

### 2. Deploy Core Infrastructure via Kustomize
```bash
kubectl apply -k deploy/k8s/
```

This provisions under the `ride-platform` namespace:
- **PostgreSQL 16:** StatefulSet + Service (`postgres.ride-platform.svc.cluster.local:5432`)
- **Redis 7.2:** StatefulSet + Service (`redis.ride-platform.svc.cluster.local:6379`)
- **Apache Kafka 3.8 (KRaft):** StatefulSet + Headless & ClusterIP Services (`kafka.ride-platform.svc.cluster.local:9092`)

### 3. Verify Deployment
```bash
kubectl get all -n ride-platform
```
