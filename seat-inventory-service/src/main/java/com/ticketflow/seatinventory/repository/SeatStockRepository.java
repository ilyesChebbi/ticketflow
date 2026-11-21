package com.ticketflow.seatinventory.repository;

import com.ticketflow.seatinventory.config.RedisConfig;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/**
 * Accès à Redis.
 *
 * Modèle de données :
 *   seats:{session_id}:{category}   -> hash { available, total }
 *   reservation:{reservation_id}    -> hash { session_id, category, quantity }
 */
@Repository
@SuppressWarnings("rawtypes")
public class SeatStockRepository {

    public static final String STOCK_PREFIX = "seats:";
    private static final String RESERVATION_PREFIX = "reservation:";

    public record Stock(int available, int total) {}

    /** Résultat d'un script Lua : code métier + stock restant. */
    public record ScriptResult(int code, int available, int total) {}

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> reserveScript;
    private final DefaultRedisScript<List> releaseScript;

    public SeatStockRepository(StringRedisTemplate redis,
                               @Qualifier("reserveScript") DefaultRedisScript<List> reserveScript,
                               @Qualifier("releaseScript") DefaultRedisScript<List> releaseScript) {
        this.redis = redis;
        this.reserveScript = reserveScript;
        this.releaseScript = releaseScript;
    }

    public Optional<Stock> findStock(String sessionId, String category) {
        List<String> values = redis.<String, String>opsForHash()
                .multiGet(stockKey(sessionId, category), List.of("available", "total"));
        if (values == null || values.get(0) == null || values.get(1) == null) {
            return Optional.empty();
        }
        return Optional.of(new Stock(Integer.parseInt(values.get(0)), Integer.parseInt(values.get(1))));
    }

    /** Toutes les catégories qui ont un stock pour cette séance (triées). */
    public List<String> findCategories(String sessionId) {
        String prefix = STOCK_PREFIX + sessionId + ":";
        Set<String> keys = redis.keys(prefix + "*");
        if (keys == null) {
            return List.of();
        }
        return keys.stream().map(key -> key.substring(prefix.length())).sorted().toList();
    }

    /** Réservation atomique (script Lua). */
    public ScriptResult reserve(String reservationId, String sessionId, String category, int quantity) {
        List<?> raw = redis.execute(
                reserveScript,
                List.of(stockKey(sessionId, category), RESERVATION_PREFIX + reservationId),
                String.valueOf(quantity), sessionId, category, RedisConfig.UPDATES_CHANNEL);
        return toResult(raw);
    }

    /** Libération atomique (script Lua). */
    public ScriptResult release(String reservationId) {
        List<?> raw = redis.execute(
                releaseScript,
                List.of(RESERVATION_PREFIX + reservationId),
                STOCK_PREFIX, RedisConfig.UPDATES_CHANNEL);
        return toResult(raw);
    }

    private ScriptResult toResult(List<?> raw) {
        if (raw == null || raw.size() < 3) {
            throw new IllegalStateException("Réponse inattendue du script Redis");
        }
        return new ScriptResult(
                ((Number) raw.get(0)).intValue(),
                ((Number) raw.get(1)).intValue(),
                ((Number) raw.get(2)).intValue());
    }

    private String stockKey(String sessionId, String category) {
        return STOCK_PREFIX + sessionId + ":" + category;
    }
}