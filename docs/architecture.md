# Architecture TicketFlow

Voir le schéma dans le rapport (§4).

## Flux principal — création d'une commande
1. Client → Gateway → `POST /api/v1/orders` (booking-service)
2. booking → seat-inventory (gRPC `ReserveSeats`, deadline 500ms, circuit breaker)
3. booking → pricing (gRPC `GetPrice`)
4. booking → payment (SOAP `authorizePayment`)
5. Si OK : persistance commande, réponse 201
6. Si SOAP Fault : rollback via gRPC `ReleaseSeats`, réponse 402