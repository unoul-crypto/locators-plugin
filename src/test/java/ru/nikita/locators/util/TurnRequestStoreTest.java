package ru.nikita.locators.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TurnRequestStoreTest {
    @Test
    void tokenIsBoundToPlayerAndCanBeUsedRepeatedly() {
        TurnRequestStore store = new TurnRequestStore();
        UUID owner = UUID.randomUUID();
        String token = store.createYaw(owner, 45.0f);

        assertFalse(store.resolve(UUID.randomUUID(), token).isPresent());
        TurnRequestStore.TurnRequest request = store.resolve(owner, token).get();
        assertEquals(45.0f, request.yaw());
        assertTrue(store.resolve(owner, token).isPresent());
    }

    @Test
    void expiredTokenIsRejected() {
        TurnRequestStore store = new TurnRequestStore(-1L);
        UUID owner = UUID.randomUUID();
        String token = store.createPitch(owner, -20.0f);

        assertFalse(store.resolve(owner, token).isPresent());
    }

    @Test
    void pitchRequestDoesNotChangeYaw() {
        TurnRequestStore store = new TurnRequestStore();
        UUID owner = UUID.randomUUID();
        String token = store.createPitch(owner, -20.0f);
        TurnRequestStore.TurnRequest request = store.resolve(owner, token).get();

        assertTrue(request.yaw() == null);
        assertEquals(-20.0f, request.pitch());
    }
}
