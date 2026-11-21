package com.ticketflow.seatinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketflow.seatinventory.grpc.AvailabilityUpdate;
import com.ticketflow.seatinventory.grpc.ChangeReason;
import com.ticketflow.seatinventory.grpc.CheckAvailabilityRequest;
import com.ticketflow.seatinventory.grpc.ReleaseSeatsRequest;
import com.ticketflow.seatinventory.grpc.ReleaseStatus;
import com.ticketflow.seatinventory.grpc.ReserveSeatsRequest;
import com.ticketflow.seatinventory.grpc.ReserveSeatsResponse;
import com.ticketflow.seatinventory.grpc.ReserveStatus;
import com.ticketflow.seatinventory.grpc.SeatInventoryServiceGrpc;
import com.ticketflow.seatinventory.grpc.WatchAvailabilityRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Test d'intégration : appelle le VRAI serveur gRPC (port 9099) avec le VRAI Redis (localhost:6379).
 * Avant de lancer : docker run -d --name ticketflow-redis -p 6379:6379 redis:7
 * Chaque test utilise une séance au nom unique : aucune donnée de démonstration n'est ajoutée au code.
 */
@SpringBootTest(properties = {"grpc.server.port=9099", "server.port=0"})
class SeatInventoryGrpcServiceTest {

    private static ManagedChannel channel;
    private static SeatInventoryServiceGrpc.SeatInventoryServiceBlockingStub stub;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        channel = ManagedChannelBuilder.forAddress("localhost", 9099).usePlaintext().build();
        stub = SeatInventoryServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void disconnect() {
        channel.shutdownNow();
    }

    @Test
    void checkAvailability_returnsTheStock() {
        String session = seedStock("VIP", 10);

        var response = stub.checkAvailability(check(session, "VIP"));

        assertThat(response.getAvailableSeats()).isEqualTo(10);
        assertThat(response.getTotalSeats()).isEqualTo(10);
    }

    @Test
    void reserveThenRelease_updatesTheStock() {
        String session = seedStock("VIP", 10);
        String reservationId = "res-" + UUID.randomUUID();

        ReserveSeatsResponse first = stub.reserveSeats(reserve(reservationId, session, "VIP", 3));
        assertThat(first.getStatus()).isEqualTo(ReserveStatus.RESERVED);
        assertThat(first.getRemainingSeats()).isEqualTo(7);

        // Même reservation_id rejoué : pas de double réservation
        ReserveSeatsResponse replay = stub.reserveSeats(reserve(reservationId, session, "VIP", 3));
        assertThat(replay.getStatus()).isEqualTo(ReserveStatus.ALREADY_RESERVED);
        assertThat(replay.getRemainingSeats()).isEqualTo(7);

        var released = stub.releaseSeats(ReleaseSeatsRequest.newBuilder().setReservationId(reservationId).build());
        assertThat(released.getStatus()).isEqualTo(ReleaseStatus.RELEASED);
        assertThat(released.getRemainingSeats()).isEqualTo(10);

        // Libérer deux fois : NOT_FOUND, sans erreur et sans remettre de places en trop
        var releasedAgain = stub.releaseSeats(ReleaseSeatsRequest.newBuilder().setReservationId(reservationId).build());
        assertThat(releasedAgain.getStatus()).isEqualTo(ReleaseStatus.NOT_FOUND);
        assertThat(stub.checkAvailability(check(session, "VIP")).getAvailableSeats()).isEqualTo(10);
    }

    @Test
    void reserveMoreThanAvailable_isRefused() {
        String session = seedStock("STANDARD", 2);

        ReserveSeatsResponse response = stub.reserveSeats(reserve("res-" + UUID.randomUUID(), session, "STANDARD", 3));

        assertThat(response.getStatus()).isEqualTo(ReserveStatus.INSUFFICIENT_SEATS);
        assertThat(response.getRemainingSeats()).isEqualTo(2);
    }

    @Test
    void unknownSession_returnsNotFound() {
        assertThatThrownBy(() -> stub.checkAvailability(check("session-inconnue-" + UUID.randomUUID(), "VIP")))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void invalidQuantity_returnsInvalidArgument() {
        String session = seedStock("VIP", 10);

        assertThatThrownBy(() -> stub.reserveSeats(reserve("res-" + UUID.randomUUID(), session, "VIP", 0)))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    /** Le test le plus important : 50 clients en même temps pour 10 places -> exactement 10 réussissent. */
    @Test
    void reserveConcurrently_neverSellsMoreThanTheStock() throws Exception {
        String session = seedStock("VIP", 10);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<ReserveStatus>> futures = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            String reservationId = "res-" + UUID.randomUUID();
            futures.add(pool.submit(() -> stub.reserveSeats(reserve(reservationId, session, "VIP", 1)).getStatus()));
        }

        int reserved = 0;
        int refused = 0;
        for (Future<ReserveStatus> future : futures) {
            ReserveStatus status = future.get(30, TimeUnit.SECONDS);
            if (status == ReserveStatus.RESERVED) {
                reserved++;
            } else if (status == ReserveStatus.INSUFFICIENT_SEATS) {
                refused++;
            }
        }
        pool.shutdown();

        assertThat(reserved).isEqualTo(10);
        assertThat(refused).isEqualTo(40);
        assertThat(stub.checkAvailability(check(session, "VIP")).getAvailableSeats()).isZero();
    }

    @Test
    void watchAvailability_streamsTheSnapshotThenEachChange() {
        String session = seedStock("VIP", 5);

        Iterator<AvailabilityUpdate> updates = stub.withDeadlineAfter(10, TimeUnit.SECONDS)
                .watchAvailability(WatchAvailabilityRequest.newBuilder().setSessionId(session).build());

        AvailabilityUpdate snapshot = updates.next();
        assertThat(snapshot.getReason()).isEqualTo(ChangeReason.SNAPSHOT);
        assertThat(snapshot.getAvailableSeats()).isEqualTo(5);

        stub.reserveSeats(reserve("res-" + UUID.randomUUID(), session, "VIP", 2));

        AvailabilityUpdate update = updates.next();
        assertThat(update.getReason()).isEqualTo(ChangeReason.RESERVED_SEATS);
        assertThat(update.getAvailableSeats()).isEqualTo(3);
        assertThat(update.getTotalSeats()).isEqualTo(5);
    }

    // ---------- Outils de test ----------

    /** Crée un stock de test directement dans Redis, sur une séance au nom unique. */
    private String seedStock(String category, int total) {
        String session = "test-" + UUID.randomUUID();
        redis.opsForHash().putAll("seats:" + session + ":" + category,
                Map.of("available", String.valueOf(total), "total", String.valueOf(total)));
        return session;
    }

    private static CheckAvailabilityRequest check(String session, String category) {
        return CheckAvailabilityRequest.newBuilder().setSessionId(session).setCategory(category).build();
    }

    private static ReserveSeatsRequest reserve(String reservationId, String session, String category, int quantity) {
        return ReserveSeatsRequest.newBuilder()
                .setReservationId(reservationId)
                .setSessionId(session)
                .setCategory(category)
                .setQuantity(quantity)
                .build();
    }
}