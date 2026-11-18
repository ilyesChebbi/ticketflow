# TicketFlow — Plateforme de billetterie en ligne

Mini-projet SOA & Services Web 2026 — Architecture microservices cloud-native.

## Stack
Java 17 · Spring Boot 3.3.x · PostgreSQL 16 · Redis 7 · Docker Compose

## Architecture
5 microservices métier + 1 API Gateway :
- `event-catalog-service` (REST)
- `booking-service` (REST — orchestrateur)
- `seat-inventory-service` (gRPC + streaming)
- `pricing-service` (gRPC)
- `payment-service` (SOAP — contract-first)
- `api-gateway` (Spring Cloud Gateway)

## Équipe
- **Membre A** — REST & Orchestration (Lead, Git)
- **Membre B** — gRPC & Streaming
- **Membre C** — SOAP, Docker & Résilience

## Lancer le projet
```bash
docker compose up --build