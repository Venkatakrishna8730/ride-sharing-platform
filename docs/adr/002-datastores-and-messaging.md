# ADR 002: Core Datastores & Event Backbone Selection

## Status
Accepted

## Context
The platform requires data stores for:
- Relational transactional state (users, trips, ledger).
- Real-time geospatial driver indexing and distributed locking.
- High-throughput asynchronous event streaming between microservices.

## Decision
1. **Relational Database: PostgreSQL 16**
   - Serves as the primary source of truth for `user-auth`, `trip`, and `payment` services.
   - Provides ACID compliance and supports the Transactional Outbox table pattern.
2. **In-Memory Store: Redis 7.2**
   - Chosen for geospatial indexing (`GEOADD`, `GEOSEARCH`) giving sub-millisecond proximity queries.
   - Used for driver presence TTL (ephemeral keys that automatically evict stale drivers).
   - Used for atomic distributed locks (`SET NX PX`) during driver matching.
3. **Event Streaming: Apache Kafka 3.8 (KRaft Mode)**
   - Employs Kafka in KRaft mode (Kafka Raft metadata mode) removing the need for an external ZooKeeper cluster.
   - Keyed partitions by `trip_id` guarantee strict per-trip event ordering.

## Trade-offs & Mitigations
- *Trade-off:* Running Kafka, Postgres, and Redis locally requires memory consideration.
- *Mitigation:* Single-node KRaft broker with low memory limits (`KAFKA_JVM_PERFORMANCE_OPTS`) keeps the entire local stack within a ~3GB RAM budget.
