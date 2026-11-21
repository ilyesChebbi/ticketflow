package com.ticketflow.seatinventory.service;

import com.ticketflow.seatinventory.grpc.AvailabilityUpdate;
import com.ticketflow.seatinventory.grpc.ChangeReason;
import com.ticketflow.seatinventory.grpc.CheckAvailabilityResponse;
import com.ticketflow.seatinventory.grpc.ReleaseSeatsResponse;
import com.ticketflow.seatinventory.grpc.ReleaseStatus;
import com.ticketflow.seatinventory.grpc.ReserveSeatsResponse;
import com.ticketflow.seatinventory.grpc.ReserveStatus;
import com.ticketflow.seatinventory.repository.SeatStockRepository;
import com.ticketflow.seatinventory.repository.SeatStockRepository.ScriptResult;
import com.ticketflow.seatinventory.repository.SeatStockRepository.Stock;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Logique métier du stock de places.
 * Erreurs : IllegalArgumentException (requête invalide) et NoSuchElementException (stock inconnu)
 * sont transformées en codes gRPC par SeatInventoryGrpcService.
 */
@Service
public class SeatInventoryService {

    private static final int CODE_NOT_FOUND = 0;
    private static final int CODE_RESERVED = 1;
    private static final int CODE_INSUFFICIENT = 2;
    private static final int CODE_ALREADY_RESERVED = 3;

    private static final int CODE_RELEASED = 1;

    private final SeatStockRepository repository;

    public SeatInventoryService(SeatStockRepository repository) {
        this.repository = repository;
    }

    public CheckAvailabilityResponse checkAvailability(String sessionId, String category) {
        requireKeyPart(sessionId, "session_id");
        requireKeyPart(category, "category");

        Stock stock = repository.findStock(sessionId, category)
                .orElseThrow(() -> stockNotFound(sessionId, category));

        return CheckAvailabilityResponse.newBuilder()
                .setSessionId(sessionId)
                .setCategory(category)
                .setAvailableSeats(stock.available())
                .setTotalSeats(stock.total())
                .build();
    }

    public ReserveSeatsResponse reserveSeats(String reservationId, String sessionId, String category, int quantity) {
        requireText(reservationId, "reservation_id");
        requireKeyPart(sessionId, "session_id");
        requireKeyPart(category, "category");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity doit être supérieure à 0");
        }

        ScriptResult result = repository.reserve(reservationId, sessionId, category, quantity);

        ReserveStatus status;
        String message;
        switch (result.code()) {
            case CODE_RESERVED -> {
                status = ReserveStatus.RESERVED;
                message = "Places réservées";
            }
            case CODE_INSUFFICIENT -> {
                status = ReserveStatus.INSUFFICIENT_SEATS;
                message = "Pas assez de places disponibles";
            }
            case CODE_ALREADY_RESERVED -> {
                status = ReserveStatus.ALREADY_RESERVED;
                message = "Cette réservation existe déjà";
            }
            case CODE_NOT_FOUND -> throw stockNotFound(sessionId, category);
            default -> throw new IllegalStateException("Code inattendu : " + result.code());
        }

        return ReserveSeatsResponse.newBuilder()
                .setStatus(status)
                .setReservationId(reservationId)
                .setRemainingSeats(result.available())
                .setMessage(message)
                .build();
    }

    public ReleaseSeatsResponse releaseSeats(String reservationId) {
        requireText(reservationId, "reservation_id");

        ScriptResult result = repository.release(reservationId);

        ReleaseStatus status = result.code() == CODE_RELEASED ? ReleaseStatus.RELEASED : ReleaseStatus.NOT_FOUND;
        return ReleaseSeatsResponse.newBuilder()
                .setStatus(status)
                .setRemainingSeats(result.available())
                .build();
    }

    /** État actuel envoyé au début de WatchAvailability (une catégorie, ou toutes si vide). */
    public List<AvailabilityUpdate> snapshot(String sessionId, String category) {
        requireKeyPart(sessionId, "session_id");

        List<String> categories;
        if (category == null || category.isBlank()) {
            categories = repository.findCategories(sessionId);
            if (categories.isEmpty()) {
                throw new NoSuchElementException("Aucun stock pour la séance " + sessionId);
            }
        } else {
            requireKeyPart(category, "category");
            categories = List.of(category);
        }

        List<AvailabilityUpdate> updates = new ArrayList<>();
        for (String cat : categories) {
            Stock stock = repository.findStock(sessionId, cat)
                    .orElseThrow(() -> stockNotFound(sessionId, cat));
            updates.add(AvailabilityUpdate.newBuilder()
                    .setSessionId(sessionId)
                    .setCategory(cat)
                    .setAvailableSeats(stock.available())
                    .setTotalSeats(stock.total())
                    .setReason(ChangeReason.SNAPSHOT)
                    .setTimestampMs(System.currentTimeMillis())
                    .build());
        }
        return updates;
    }

    private NoSuchElementException stockNotFound(String sessionId, String category) {
        return new NoSuchElementException("Aucun stock pour la séance " + sessionId + " / catégorie " + category);
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " est obligatoire");
        }
    }

    /** session_id et category entrent dans les clés Redis et dans les messages pub/sub : ':' et '|' sont interdits. */
    private void requireKeyPart(String value, String field) {
        requireText(value, field);
        if (value.contains(":") || value.contains("|")) {
            throw new IllegalArgumentException(field + " ne doit pas contenir ':' ni '|'");
        }
    }
}