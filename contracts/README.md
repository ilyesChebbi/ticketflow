# Guide d'intégration gRPC — pour `booking-service` (Membre A)

Écrit par Membre B. Concerne `seat-inventory-service` et `pricing-service`.
**Le code client ci-dessous n'a pas été compilé ni testé** (`booking-service` n'existe pas encore dans le dépôt) : seule sa syntaxe a été vérifiée. Les deux services, eux, sont testés (24 tests verts).

## 1. Contrats (source de vérité)
- `contracts/proto/seat_inventory.proto` — package `ticketflow.seatinventory.v1`, classes Java `com.ticketflow.seatinventory.grpc`
- `contracts/proto/pricing.proto` — package `ticketflow.pricing.v1`, classes Java `com.ticketflow.pricing.grpc`

Ne pas copier les `.proto` : pointer le plugin Maven vers `../contracts/proto`.

## 2. Adresses
| Service | gRPC | HTTP (health) |
|---|---|---|
| seat-inventory-service | 9090 | 8083 |
| pricing-service | 9091 | 8084 |

En local : `localhost`. Dans Docker Compose : nom du service (à confirmer avec Membre C), via `grpc.seat-inventory.host` et `grpc.pricing.host`.

## 3. Conventions à respecter
- **Montants en millimes de TND** (1 TND = 1000 millimes), type `int64`. `108000` = `108.000 TND`.
- **Catégories** : texte exact, sensible à la casse (`STANDARD`, `VIP` pour l'instant ; elles vivent dans la base SQL de pricing, pas dans le code). `session_id` et `category` ne contiennent ni `:` ni `|`.
- **Une `ReserveSeats` par catégorie**. `reservation_id` = clé d'idempotence (ex. `Idempotency-Key + ":" + category`) : rejouer le même id renvoie `ALREADY_RESERVED`, sans double réservation.
- Si le paiement ou le prix échoue après la réservation : appeler `ReleaseSeats(reservation_id)`.
- Délais (deadlines) : 500 ms seat-inventory, 1000 ms pricing.

## 4. Erreurs gRPC → HTTP
| Situation | Résultat | HTTP conseillé |
|---|---|---|
| `ReserveStatus.INSUFFICIENT_SEATS` | réponse normale | 409 |
| `ReserveStatus.ALREADY_RESERVED` | réponse normale (commande rejouée) | succès |
| `promo_status = PROMO_INVALID` | réponse normale (prix sans remise) | à toi de décider (ignorer ou 400) |
| `INVALID_ARGUMENT` | exception | 400 |
| `NOT_FOUND` (catégorie/prix inconnu) | exception | 404 ou 409 |
| `UNAVAILABLE` / `DEADLINE_EXCEEDED` | exception | 503 |

## 5. `pom.xml` de booking-service (gRPC client simple, sans starter Spring)
Versions identiques aux miennes : grpc 1.63.0, protobuf 3.25.3, javax.annotation-api 1.3.2.

```xml
<properties>
  <grpc.version>1.63.0</grpc.version>
  <protobuf.version>3.25.3</protobuf.version>
</properties>

<dependencies>
  <dependency><groupId>io.grpc</groupId><artifactId>grpc-netty-shaded</artifactId><version>${grpc.version}</version></dependency>
  <dependency><groupId>io.grpc</groupId><artifactId>grpc-protobuf</artifactId><version>${grpc.version}</version></dependency>
  <dependency><groupId>io.grpc</groupId><artifactId>grpc-stub</artifactId><version>${grpc.version}</version></dependency>
  <dependency><groupId>com.google.protobuf</groupId><artifactId>protobuf-java</artifactId><version>${protobuf.version}</version></dependency>
  <dependency><groupId>javax.annotation</groupId><artifactId>javax.annotation-api</artifactId><version>1.3.2</version><scope>provided</scope></dependency>
</dependencies>

<build>
  <extensions>
    <extension><groupId>kr.motd.maven</groupId><artifactId>os-maven-plugin</artifactId><version>1.7.1</version></extension>
  </extensions>
  <plugins>
    <plugin>
      <groupId>org.xolstice.maven.plugins</groupId>
      <artifactId>protobuf-maven-plugin</artifactId>
      <version>0.6.1</version>
      <configuration>
        <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
        <protoSourceRoot>${project.basedir}/../contracts/proto</protoSourceRoot>
      </configuration>
      <executions><execution><goals><goal>compile</goal><goal>compile-custom</goal></goals></execution></executions>
    </plugin>
  </plugins>
</build>
```


## 6. `application.yml` de booking-service
```yaml
grpc:
  seat-inventory:
    host: ${SEAT_INVENTORY_HOST:localhost}
    port: ${SEAT_INVENTORY_PORT:9090}
  pricing:
    host: ${PRICING_HOST:localhost}
    port: ${PRICING_PORT:9091}
```

## 7. Code client (exemple, non testé)

### `GrpcClientConfig.java` (package `com.ticketflow.booking.config`)
```java
package com.ticketflow.booking.config;

import com.ticketflow.pricing.grpc.PricingServiceGrpc;
import com.ticketflow.seatinventory.grpc.SeatInventoryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connexions gRPC vers seat-inventory-service et pricing-service.
 * Un ManagedChannel est une connexion réutilisable et thread-safe : on en crée UN par service, pas un par appel.
 */
@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel seatInventoryChannel(
            @Value("${grpc.seat-inventory.host:localhost}") String host,
            @Value("${grpc.seat-inventory.port:9090}") int port) {
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel pricingChannel(
            @Value("${grpc.pricing.host:localhost}") String host,
            @Value("${grpc.pricing.port:9091}") int port) {
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean
    public SeatInventoryServiceGrpc.SeatInventoryServiceBlockingStub seatInventoryStub(
            @Qualifier("seatInventoryChannel") ManagedChannel channel) {
        return SeatInventoryServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public PricingServiceGrpc.PricingServiceBlockingStub pricingStub(
            @Qualifier("pricingChannel") ManagedChannel channel) {
        return PricingServiceGrpc.newBlockingStub(channel);
    }
}
```

### `SeatInventoryClient.java` (package `com.ticketflow.booking.client`)
```java
package com.ticketflow.booking.client;

import com.ticketflow.seatinventory.grpc.ReleaseSeatsRequest;
import com.ticketflow.seatinventory.grpc.ReleaseSeatsResponse;
import com.ticketflow.seatinventory.grpc.ReserveSeatsRequest;
import com.ticketflow.seatinventory.grpc.ReserveSeatsResponse;
import com.ticketflow.seatinventory.grpc.SeatInventoryServiceGrpc;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Appelle seat-inventory-service avec un délai maximum de 500 ms (docs/architecture.md).
 * Si le délai est dépassé ou si le service est arrêté, gRPC lève une StatusRuntimeException
 * (DEADLINE_EXCEEDED ou UNAVAILABLE) : c'est l'appelant (OrderService) qui la traduit en HTTP 503.
 */
@Component
public class SeatInventoryClient {

    private static final long DEADLINE_MS = 500;

    private final SeatInventoryServiceGrpc.SeatInventoryServiceBlockingStub stub;

    public SeatInventoryClient(SeatInventoryServiceGrpc.SeatInventoryServiceBlockingStub stub) {
        this.stub = stub;
    }

    /** reservationId = clé d'idempotence : le même id rejoué ne réserve jamais deux fois (ALREADY_RESERVED). */
    public ReserveSeatsResponse reserve(String reservationId, String sessionId, String category, int quantity) {
        ReserveSeatsRequest request = ReserveSeatsRequest.newBuilder()
                .setReservationId(reservationId)
                .setSessionId(sessionId)
                .setCategory(category)
                .setQuantity(quantity)
                .build();
        return stub.withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS).reserveSeats(request);
    }

    /** Compensation : libère la réservation. NOT_FOUND (déjà libérée) est un succès, on peut l'ignorer. */
    public ReleaseSeatsResponse release(String reservationId) {
        ReleaseSeatsRequest request = ReleaseSeatsRequest.newBuilder()
                .setReservationId(reservationId)
                .build();
        return stub.withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS).releaseSeats(request);
    }
}
```

### `PricingClient.java` (package `com.ticketflow.booking.client`)
```java
package com.ticketflow.booking.client;

import com.ticketflow.pricing.grpc.GetPriceRequest;
import com.ticketflow.pricing.grpc.GetPriceResponse;
import com.ticketflow.pricing.grpc.PricingServiceGrpc;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Appelle pricing-service (GetPrice, appel unaire). Tous les montants reçus sont en MILLIMES de TND. */
@Component
public class PricingClient {

    private static final long DEADLINE_MS = 1000;

    private final PricingServiceGrpc.PricingServiceBlockingStub stub;

    public PricingClient(PricingServiceGrpc.PricingServiceBlockingStub stub) {
        this.stub = stub;
    }

    /**
     * @param sessionDate date de la séance au format ISO (ex. "2026-12-15T20:30:00"), ou vide
     * @param promoCode   code promo, ou vide s'il n'y en a pas
     */
    public GetPriceResponse getPrice(String sessionId, String category, int quantity,
                                     String sessionDate, String promoCode) {
        GetPriceRequest request = GetPriceRequest.newBuilder()
                .setSessionId(sessionId)
                .setCategory(category)
                .setQuantity(quantity)
                .setSessionDate(sessionDate == null ? "" : sessionDate)
                .setPromoCode(promoCode == null ? "" : promoCode)
                .build();
        return stub.withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS).getPrice(request);
    }
}
```

### `OrderFlowExample.java` (package `com.ticketflow.booking.service`)
```java
package com.ticketflow.booking.service;

import com.ticketflow.booking.client.PricingClient;
import com.ticketflow.booking.client.SeatInventoryClient;
import com.ticketflow.pricing.grpc.GetPriceResponse;
import com.ticketflow.seatinventory.grpc.ReserveSeatsResponse;
import com.ticketflow.seatinventory.grpc.ReserveStatus;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * EXEMPLE du flux de docs/architecture.md (étapes 2, 3 et 6) : à adapter au vrai OrderService de booking-service.
 *   2. ReserveSeats  3. GetPrice  4. paiement SOAP  6. si le paiement échoue : ReleaseSeats (compensation)
 */
@Service
public class OrderFlowExample {

    private final SeatInventoryClient seatInventory;
    private final PricingClient pricing;

    public OrderFlowExample(SeatInventoryClient seatInventory, PricingClient pricing) {
        this.seatInventory = seatInventory;
        this.pricing = pricing;
    }

    /** Exemple de retour : montant à payer en TND (3 décimales). À remplacer par ton objet Order. */
    public BigDecimal createOrder(String idempotencyKey, String sessionId, String category, int quantity,
                                  String sessionDate, String promoCode) {
        // L'Idempotency-Key du header REST sert de reservation_id : rejouer la même commande ne réserve pas deux fois.
        String reservationId = idempotencyKey + ":" + category;

        // 2. Réserver les places
        ReserveSeatsResponse reserved;
        try {
            reserved = seatInventory.reserve(reservationId, sessionId, category, quantity);
        } catch (StatusRuntimeException e) {
            throw translate(e);
        }
        if (reserved.getStatus() == ReserveStatus.INSUFFICIENT_SEATS) {
            throw new IllegalStateException("409 Conflict : pas assez de places");
        }
        // RESERVED et ALREADY_RESERVED (commande rejouée) sont tous les deux des succès.

        try {
            // 3. Calculer le prix (en millimes)
            GetPriceResponse price = pricing.getPrice(sessionId, category, quantity, sessionDate, promoCode);
            BigDecimal totalTnd = BigDecimal.valueOf(price.getTotalMillimes(), 3); // 108000 -> 108.000

            // 4. Paiement SOAP : authorizePayment(totalTnd) ... (Membre C)
            //    Si le paiement échoue (SOAP Fault) : lever une exception -> le catch ci-dessous libère les places.
            return totalTnd;
        } catch (RuntimeException e) {
            // 6. Compensation : on libère les places si quoi que ce soit échoue après la réservation.
            try {
                seatInventory.release(reservationId);
            } catch (StatusRuntimeException ignored) {
                // À journaliser : si la libération échoue, les places restent bloquées (à rattraper par un job).
            }
            if (e instanceof StatusRuntimeException statusError) {
                throw translate(statusError);
            }
            throw e;
        }
    }

    /** Traduction des codes gRPC en réponses REST (à adapter au GlobalExceptionHandler de booking-service). */
    private RuntimeException translate(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        return switch (code) {
            case UNAVAILABLE, DEADLINE_EXCEEDED -> new IllegalStateException("503 Service Unavailable : " + code);
            case NOT_FOUND -> new IllegalStateException("404/409 : séance ou catégorie inconnue");
            case INVALID_ARGUMENT -> new IllegalArgumentException("400 Bad Request : " + e.getStatus().getDescription());
            default -> new IllegalStateException("500 : erreur gRPC " + code);
        };
    }
}
```

## 8. Tester en local
1. Redis : `docker start ticketflow-redis` ; créer du stock (aucune donnée de démo n'est chargée) :
   `docker exec ticketflow-redis redis-cli HSET seats:session-1:VIP available 100 total 100`
2. PostgreSQL pricing : conteneur `ticketflow-pricing-db` (port hôte 5433). Variables : `DB_URL`, `DB_USER`, `DB_PASSWORD` (le mot de passe n'est jamais dans le code).
3. Lancer `seat-inventory-service` (8083/9090) et `pricing-service` (8084/9091) avec `.\mvnw.cmd spring-boot:run`.
4. Vérifier : `http://localhost:8083/actuator/health` et `http://localhost:8084/actuator/health` → `UP`.