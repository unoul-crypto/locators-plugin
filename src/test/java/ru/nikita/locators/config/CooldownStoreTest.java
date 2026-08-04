package ru.nikita.locators.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.nikita.locators.model.LocatorDefinition;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CooldownStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingCooldownModeUsesBackwardCompatibleModeOne() {
        LocatorDefinition oldDefinition = new Gson().fromJson("{}", LocatorDefinition.class);
        LocatorDefinition individualDefinition = new Gson().fromJson("{\"cooldownMode\":2}",
                LocatorDefinition.class);

        assertEquals(1, oldDefinition.cooldownMode());
        assertEquals(2, individualDefinition.cooldownMode());
    }

    @Test
    void savesAndLoadsBothCooldownModes() throws Exception {
        Path file = temporaryDirectory.resolve("cooldowns.json");
        UUID playerId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        long readyAt = System.currentTimeMillis() + 60_000L;

        CooldownStore written = new CooldownStore(file);
        written.startType(playerId, "basic", readyAt);
        written.startItem(instanceId, readyAt);
        written.save();

        CooldownStore loaded = new CooldownStore(file);
        loaded.load();

        assertEquals(2, loaded.size());
        assertTrue(loaded.remainingTypeMillis(playerId, "BASIC", System.currentTimeMillis()) > 0);
        assertTrue(loaded.remainingItemMillis(instanceId, System.currentTimeMillis()) > 0);
    }

    @Test
    void resetsOnlyMatchingTypeCooldowns() {
        CooldownStore store = new CooldownStore(temporaryDirectory.resolve("cooldowns.json"));
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        long readyAt = System.currentTimeMillis() + 60_000L;
        store.startType(firstPlayer, "basic", readyAt);
        store.startType(firstPlayer, "advanced", readyAt);
        store.startType(secondPlayer, "basic", readyAt);

        assertEquals(1, store.resetTypeCooldowns("basic", firstPlayer));
        assertEquals(1, store.resetTypeCooldowns(null, firstPlayer));
        assertEquals(1, store.resetTypeCooldowns("basic", null));
        assertEquals(0, store.size());
    }

    @Test
    void expiredCooldownsAreNotLoaded() throws Exception {
        Path file = temporaryDirectory.resolve("cooldowns.json");
        CooldownStore written = new CooldownStore(file);
        written.startType(UUID.randomUUID(), "basic", System.currentTimeMillis() - 1L);
        written.startItem(UUID.randomUUID(), System.currentTimeMillis() - 1L);
        written.save();

        CooldownStore loaded = new CooldownStore(file);
        loaded.load();
        assertEquals(0, loaded.size());
    }
}
