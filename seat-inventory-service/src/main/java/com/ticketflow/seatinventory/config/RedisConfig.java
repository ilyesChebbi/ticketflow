package com.ticketflow.seatinventory.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@SuppressWarnings("rawtypes")
public class RedisConfig {

    /** Canal Redis pub/sub sur lequel les scripts publient chaque changement de stock. */
    public static final String UPDATES_CHANNEL = "seat-updates";

    @Bean
    public DefaultRedisScript<List> reserveScript() {
        return loadScript("redis/reserve.lua");
    }

    @Bean
    public DefaultRedisScript<List> releaseScript() {
        return loadScript("redis/release.lua");
    }

    /** Écoute le canal pub/sub (utilisé par AvailabilityBroadcaster pour WatchAvailability). */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    private DefaultRedisScript<List> loadScript(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }
}