package com.ticketflow.seatinventory.grpc;

import com.ticketflow.seatinventory.service.AvailabilityBroadcaster;
import com.ticketflow.seatinventory.service.SeatInventoryService;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * Point d'entrée gRPC (port 9090). Les classes SeatInventoryServiceGrpc, CheckAvailabilityRequest, etc.
 * sont générées automatiquement à partir de contracts/proto/seat_inventory.proto.
 *
 * Conventions d'erreur :
 *   requête invalide -> INVALID_ARGUMENT, stock inconnu -> NOT_FOUND, Redis KO -> UNAVAILABLE.
 * (« pas assez de places » n'est PAS une erreur : c'est le status INSUFFICIENT_SEATS dans la réponse.)
 */
@GrpcService
public class SeatInventoryGrpcService extends SeatInventoryServiceGrpc.SeatInventoryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(SeatInventoryGrpcService.class);

    private final SeatInventoryService service;
    private final AvailabilityBroadcaster broadcaster;

    public SeatInventoryGrpcService(SeatInventoryService service, AvailabilityBroadcaster broadcaster) {
        this.service = service;
        this.broadcaster = broadcaster;
    }

    @Override
    public void checkAvailability(CheckAvailabilityRequest request,
                                  StreamObserver<CheckAvailabilityResponse> responseObserver) {
        reply(responseObserver, () -> service.checkAvailability(request.getSessionId(), request.getCategory()));
    }

    @Override
    public void reserveSeats(ReserveSeatsRequest request, StreamObserver<ReserveSeatsResponse> responseObserver) {
        reply(responseObserver, () -> service.reserveSeats(
                request.getReservationId(), request.getSessionId(), request.getCategory(), request.getQuantity()));
    }

    @Override
    public void releaseSeats(ReleaseSeatsRequest request, StreamObserver<ReleaseSeatsResponse> responseObserver) {
        reply(responseObserver, () -> service.releaseSeats(request.getReservationId()));
    }

    /** Server streaming : on garde la connexion ouverte et on envoie un message à chaque changement de stock. */
    @Override
    public void watchAvailability(WatchAvailabilityRequest request, StreamObserver<AvailabilityUpdate> responseObserver) {
        ServerCallStreamObserver<AvailabilityUpdate> observer =
                (ServerCallStreamObserver<AvailabilityUpdate>) responseObserver;

        // 1. On s'abonne d'abord (pour ne rater aucun changement)...
        AvailabilityBroadcaster.Subscription subscription =
                broadcaster.subscribe(request.getSessionId(), request.getCategory(), observer);
        // ... et on se désabonne quand le client coupe la connexion.
        observer.setOnCancelHandler(() -> broadcaster.unsubscribe(subscription));

        // 2. On envoie l'état actuel (SNAPSHOT). Ensuite les mises à jour arrivent toutes seules.
        try {
            for (AvailabilityUpdate snapshot : service.snapshot(request.getSessionId(), request.getCategory())) {
                broadcaster.send(subscription, snapshot);
            }
        } catch (RuntimeException e) {
            broadcaster.unsubscribe(subscription);
            observer.onError(toStatus(e).asRuntimeException());
        }
    }

    private <T> void reply(StreamObserver<T> observer, Supplier<T> action) {
        try {
            T result = action.get();
            observer.onNext(result);
            observer.onCompleted();
        } catch (RuntimeException e) {
            observer.onError(toStatus(e).asRuntimeException());
        }
    }

    private Status toStatus(RuntimeException e) {
        if (e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(e.getMessage());
        }
        if (e instanceof NoSuchElementException) {
            return Status.NOT_FOUND.withDescription(e.getMessage());
        }
        if (e instanceof RedisConnectionFailureException || e instanceof QueryTimeoutException) {
            log.error("Redis indisponible", e);
            return Status.UNAVAILABLE.withDescription("Redis indisponible");
        }
        log.error("Erreur inattendue", e);
        return Status.INTERNAL.withDescription("Erreur interne");
    }
}