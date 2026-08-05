package ru.nikita.locators.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One-time, player-bound tokens used by clickable chat angles. */
public final class TurnRequestStore {
    private static final long DEFAULT_TTL_MILLIS = 120_000L;

    private final Map<String, StoredRequest> requests = new HashMap<String, StoredRequest>();
    private final long ttlMillis;

    public TurnRequestStore() {
        this(DEFAULT_TTL_MILLIS);
    }

    TurnRequestStore(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public String createYaw(UUID playerId, float yaw) {
        return create(playerId, yaw, null);
    }

    public String createPitch(UUID playerId, float pitch) {
        return create(playerId, null, pitch);
    }

    public Optional<TurnRequest> consume(UUID playerId, String token) {
        long now = System.currentTimeMillis();
        StoredRequest stored = requests.get(token);
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.expiresAtMillis <= now) {
            requests.remove(token);
            return Optional.empty();
        }
        if (!stored.playerId.equals(playerId)) {
            return Optional.empty();
        }
        requests.remove(token);
        return Optional.of(new TurnRequest(stored.yaw, stored.pitch));
    }

    private String create(UUID playerId, Float yaw, Float pitch) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "");
        } while (requests.containsKey(token));
        requests.put(token, new StoredRequest(playerId, yaw, pitch, now + ttlMillis));
        return token;
    }

    private void pruneExpired(long now) {
        Iterator<Map.Entry<String, StoredRequest>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAtMillis <= now) {
                iterator.remove();
            }
        }
    }

    public static final class TurnRequest {
        private final Float yaw;
        private final Float pitch;

        private TurnRequest(Float yaw, Float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Float yaw() {
            return yaw;
        }

        public Float pitch() {
            return pitch;
        }
    }

    private static final class StoredRequest {
        private final UUID playerId;
        private final Float yaw;
        private final Float pitch;
        private final long expiresAtMillis;

        private StoredRequest(UUID playerId, Float yaw, Float pitch, long expiresAtMillis) {
            this.playerId = playerId;
            this.yaw = yaw;
            this.pitch = pitch;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
