# 🎟️ TicketFlow — Plateforme de Billetterie en Ligne

**Mini-projet Architecture SOA et Services Web — 2026**

TicketFlow est une application à microservices cloud-native conçue pour gérer la réservation de places de spectacle en temps réel. Le projet démontre l'utilisation combinée de trois styles d'architecture orientée services (SOA) : **REST**, **gRPC** et **SOAP**, le tout conteneurisé et orchestré avec Docker Compose.

## 🏗️ Architecture du Système

L'application est divisée en **5 microservices métier** et **1 API Gateway**, chacun possédant sa propre base de données ou son propre stockage (polyglot persistence).

| Service | Protocole | Responsabilité | Stockage | Justification du protocole |
| :--- | :--- | :--- | :--- | :--- |
| **api-gateway** | HTTP / REST | Point d'entrée unique, routage | Aucun | Centralise les requêtes clients |
| **event-catalog-service** | REST | Catalogue des événements, salles | PostgreSQL | Exposition web/mobile, ressources cacheables, OpenAPI |
| **booking-service** | REST | Orchestration (Saga), Commandes | PostgreSQL | CRUD client, Idempotence |
| **seat-inventory-service** | gRPC | Stock de places, réservations atomiques | Redis | Haute performance interne, streaming temps réel |
| **pricing-service** | gRPC | Calcul des prix dynamiques, promotions | PostgreSQL | Calcul intensif, format binaire compact |
| **payment-service** | SOAP | Passerelle de paiement bancaire | PostgreSQL | Interopérabilité B2B/legacy, contrat strict XSD/WSDL |

### Flux Principal (Pattern Saga)
Lorsqu'un client crée une commande via le `booking-service` :
1. **Idempotence** : Vérification de la clé `Idempotency-Key` pour éviter les doubles débits.
2. **Réservation (gRPC)** : Appel au `seat-inventory-service` pour bloquer les places.
3. **Tarification (gRPC)** : Appel au `pricing-service` pour calculer le prix total.
4. **Paiement (SOAP)** : Appel au `payment-service` pour autoriser la transaction.
5. **Compensation** : En cas d'échec du paiement, appel gRPC `ReleaseSeats` pour libérer les places (Rollback distribué).

## 🚀 Prérequis
*   **Docker** et **Docker Compose** installés sur votre machine.
*   Java 17 (uniquement pour le développement local, pas nécessaire pour lancer l'application).
*   Git.

## 🛠️ Lancer le projet

Le projet est entièrement conteneurisé. Une seule commande suffit pour démarrer les bases de données, l'infrastructure et les 6 services.

```bash
# 1. Cloner le dépôt
git clone https://github.com/ilyesChebbi/ticketflow.git
cd ticketflow

# 2. Configurer les variables d'environnement
cp .env.example .env

# 3. Lancer l'orchestration
docker compose up -d --build
