package dev.locators.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Stores absolute cooldown expiry timestamps so they survive server restarts. */
public final class CooldownStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<TypeKey, Long> typeCooldowns = new LinkedHashMap<TypeKey, Long>();
    private final Map<UUID, Long> itemCooldowns = new LinkedHashMap<UUID, Long>();

    public CooldownStore(Path file) {
        this.file = file;
    }

    public void load() throws IOException {
        typeCooldowns.clear();
        itemCooldowns.clear();
        if (!Files.exists(file)) {
            return;
        }

        Snapshot snapshot;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            snapshot = GSON.fromJson(reader, Snapshot.class);
        } catch (JsonParseException exception) {
            throw new IOException("Некорректный JSON в " + file.getFileName() + ": " + exception.getMessage(), exception);
        }
        if (snapshot == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (snapshot.typeCooldowns != null) {
            for (TypeEntry entry : snapshot.typeCooldowns) {
                if (entry == null || entry.playerId == null || entry.locatorId == null
                        || entry.readyAtEpochMillis <= now) {
                    continue;
                }
                try {
                    UUID playerId = UUID.fromString(entry.playerId);
                    typeCooldowns.put(new TypeKey(playerId, normalize(entry.locatorId)), entry.readyAtEpochMillis);
                } catch (IllegalArgumentException ignored) {
                    // A single malformed entry must not prevent all valid cooldowns from loading.
                }
            }
        }
        if (snapshot.itemCooldowns != null) {
            for (ItemEntry entry : snapshot.itemCooldowns) {
                if (entry == null || entry.instanceId == null || entry.readyAtEpochMillis <= now) {
                    continue;
                }
                try {
                    itemCooldowns.put(UUID.fromString(entry.instanceId), entry.readyAtEpochMillis);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed individual entries.
                }
            }
        }
    }

    public void save() throws IOException {
        long now = System.currentTimeMillis();
        pruneExpired(now);

        Snapshot snapshot = new Snapshot();
        snapshot.version = 1;
        snapshot.typeCooldowns = new ArrayList<TypeEntry>();
        for (Map.Entry<TypeKey, Long> entry : typeCooldowns.entrySet()) {
            TypeEntry serialized = new TypeEntry();
            serialized.playerId = entry.getKey().playerId.toString();
            serialized.locatorId = entry.getKey().locatorId;
            serialized.readyAtEpochMillis = entry.getValue();
            snapshot.typeCooldowns.add(serialized);
        }
        snapshot.itemCooldowns = new ArrayList<ItemEntry>();
        for (Map.Entry<UUID, Long> entry : itemCooldowns.entrySet()) {
            ItemEntry serialized = new ItemEntry();
            serialized.instanceId = entry.getKey().toString();
            serialized.readyAtEpochMillis = entry.getValue();
            snapshot.itemCooldowns.add(serialized);
        }

        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            GSON.toJson(snapshot, writer);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public long remainingTypeMillis(UUID playerId, String locatorId, long now) {
        return remaining(typeCooldowns.get(new TypeKey(playerId, normalize(locatorId))), now);
    }

    public long remainingItemMillis(UUID instanceId, long now) {
        return remaining(itemCooldowns.get(instanceId), now);
    }

    public void startType(UUID playerId, String locatorId, long readyAtEpochMillis) {
        typeCooldowns.put(new TypeKey(playerId, normalize(locatorId)), readyAtEpochMillis);
    }

    public void startItem(UUID instanceId, long readyAtEpochMillis) {
        itemCooldowns.put(instanceId, readyAtEpochMillis);
    }

    public int resetTypeCooldowns(String locatorId, UUID playerId) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        String normalizedLocatorId = locatorId == null ? null : normalize(locatorId);
        int removed = 0;
        Iterator<Map.Entry<TypeKey, Long>> iterator = typeCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            TypeKey key = iterator.next().getKey();
            if ((normalizedLocatorId == null || key.locatorId.equals(normalizedLocatorId))
                    && (playerId == null || key.playerId.equals(playerId))) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public int size() {
        return typeCooldowns.size() + itemCooldowns.size();
    }

    private long remaining(Long readyAt, long now) {
        if (readyAt == null || readyAt <= now) {
            return 0L;
        }
        return readyAt - now;
    }

    private void pruneExpired(long now) {
        removeExpired(typeCooldowns, now);
        removeExpired(itemCooldowns, now);
    }

    private <K> void removeExpired(Map<K, Long> values, long now) {
        Iterator<Map.Entry<K, Long>> iterator = values.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }
    }

    private static String normalize(String locatorId) {
        return locatorId.toLowerCase(Locale.ROOT);
    }

    private static final class TypeKey {
        private final UUID playerId;
        private final String locatorId;

        private TypeKey(UUID playerId, String locatorId) {
            this.playerId = playerId;
            this.locatorId = locatorId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TypeKey)) {
                return false;
            }
            TypeKey that = (TypeKey) other;
            return playerId.equals(that.playerId) && locatorId.equals(that.locatorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, locatorId);
        }
    }

    private static final class Snapshot {
        private int version;
        private List<TypeEntry> typeCooldowns;
        private List<ItemEntry> itemCooldowns;
    }

    private static final class TypeEntry {
        private String playerId;
        private String locatorId;
        private long readyAtEpochMillis;
    }

    private static final class ItemEntry {
        private String instanceId;
        private long readyAtEpochMillis;
    }
}
