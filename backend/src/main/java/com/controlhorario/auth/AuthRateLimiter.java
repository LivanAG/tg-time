package com.controlhorario.auth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cubos de Bucket4j en memoria, uno por clave (endpoint + IP). El mapa está acotado: los contadores
 * sin uso durante un periodo completo (que ya estarían llenos) se purgan cada minuto y también al
 * llegar a {@code maxEntries}.
 */
@Component
public class AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);

    private final RateLimitProperties properties;
    private final LongSupplier nanoTime;
    private final ConcurrentMap<String, Entry> buckets = new ConcurrentHashMap<>();

    private record Entry(Bucket bucket, AtomicLong lastAccessNanos) {
    }

    @Autowired
    public AuthRateLimiter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    AuthRateLimiter(RateLimitProperties properties, LongSupplier nanoTime) {
        if (properties.capacity() < 1 || properties.period().isZero() || properties.period().isNegative()) {
            throw new IllegalArgumentException("app.rate-limit necesita capacity >= 1 y un periodo positivo");
        }
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    /**
     * Consume una petición de {@code key}. Devuelve 0 si se permite o, si no, los segundos que hay
     * que esperar (valor de la cabecera Retry-After, mínimo 1).
     */
    public long tryConsume(String key) {
        long now = nanoTime.getAsLong();
        if (buckets.size() >= properties.maxEntries()) {
            evictIdle(now);
        }
        Entry entry = buckets.computeIfAbsent(key, k -> new Entry(newBucket(), new AtomicLong(now)));
        entry.lastAccessNanos().set(now);
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return 0;
        }
        long seconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + TimeUnit.SECONDS.toNanos(1) - 1);
        return Math.max(1, seconds);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void cleanup() {
        evictIdle(nanoTime.getAsLong());
    }

    int size() {
        return buckets.size();
    }

    void evictIdle(long now) {
        long idleNanos = properties.period().toNanos();
        buckets.entrySet().removeIf(e -> now - e.getValue().lastAccessNanos().get() > idleNanos);
        if (buckets.size() >= properties.maxEntries()) {
            // Muchas IPs distintas en menos de un periodo: se prioriza acotar la memoria.
            log.warn("Rate limit de /api/auth: {} contadores activos (máximo {}); se reinician",
                    buckets.size(), properties.maxEntries());
            buckets.clear();
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(properties.capacity())
                        .refillIntervally(properties.capacity(), properties.period()))
                .build();
    }
}
