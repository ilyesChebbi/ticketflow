package com.ticketflow.seatinventory.service;

import com.ticketflow.seatinventory.config.RedisConfig;
import com.ticketflow.seatinventory.grpc.AvailabilityUpdate;
import com.ticketflow.seatinventory.grpc.ChangeReason;
import io.grpc.stub.ServerCallStreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Pont entre Redis pub/sub et les clients gRPC qui font WatchAvailability.
 * Les scripts Lua publient un message à chaque changement de stock ;
 * ce composant le transmet à tous les clients abonnés à cette séance / catégorie.
 */
@Component
public class AvailabilityBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityBroadcaster.class);

    /** Un client abonné. category vide = toutes les catégories de la séance. */
    public static final class Subscription {
        private final String sessionId;
        private final String category;
        private final ServerCallStreamObserver<AvailabilityUpdate> observer;

        private Subscription(String sessionId, String category, ServerCallStreamObserver<AvailabilityUpdate> observer) {
            this.sessionId = sessionId;
            this.category = category;
            this.observer = observer;
        }

        private boolean matches(String updateSessionId, String updateCategory) {
            return sessionId.equals(updateSessionId) && (category.isEmpty() || category.equals(updateCategory));
        }
    }

    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    public AvailabilityBroadcaster(RedisMessageListenerContainer container) {
        container.addMessageListener(this::onMessage, new ChannelTopic(RedisConfig.UPDATES_CHANNEL));
    }

    public Subscription subscribe(String sessionId, String category,
                                  ServerCallStreamObserver<AvailabilityUpdate> observer) {
        Subscription subscription = new Subscription(sessionId, category == null ? "" : category, observer);
        subscriptions.add(subscription);
        return subscription;
    }

    public void unsubscribe(Subscription subscription) {
        subscriptions.remove(subscription);
    }

    /** Envoie un message à un client (synchronisé : un StreamObserver ne supporte pas les écritures concurrentes). */
    public void send(Subscription subscription, AvailabilityUpdate update) {
        ServerCallStreamObserver<AvailabilityUpdate> observer = subscription.observer;
        synchronized (observer) {
            try {
                if (!observer.isCancelled()) {
                    observer.onNext(update);
                }
            } catch (RuntimeException e) {
                log.debug("Client déconnecté, abonnement supprimé", e);
                subscriptions.remove(subscription);
            }
        }
    }

    /** Format publié par les scripts Lua : session|category|available|total|REASON */
    private void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String[] parts = body.split("\\|", -1);
        if (parts.length != 5) {
            log.warn("Message pub/sub ignoré (format inattendu) : {}", body);
            return;
        }

        AvailabilityUpdate update;
        try {
            update = AvailabilityUpdate.newBuilder()
                    .setSessionId(parts[0])
                    .setCategory(parts[1])
                    .setAvailableSeats(Integer.parseInt(parts[2]))
                    .setTotalSeats(Integer.parseInt(parts[3]))
                    .setReason(ChangeReason.valueOf(parts[4]))
                    .setTimestampMs(System.currentTimeMillis())
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Message pub/sub ignoré (valeurs invalides) : {}", body);
            return;
        }

        for (Subscription subscription : subscriptions) {
            if (subscription.matches(update.getSessionId(), update.getCategory())) {
                send(subscription, update);
            }
        }
    }
}