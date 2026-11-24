package app.gameday.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrashGuardTest {

    private static final long MINUTE = 60_000L;

    @Test
    public void freshInstallIsNotSafeMode() {
        CrashGuard guard = new CrashGuard(new MemoryKeyValueStore());
        assertFalse(guard.shouldEnterSafeMode(1_000_000L));
    }

    @Test
    public void threeBootsInsideWindowEnterSafeMode() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        CrashGuard guard = new CrashGuard(store, 3, 5 * MINUTE);
        guard.recordBoot(1 * MINUTE);
        guard.recordBoot(2 * MINUTE);
        assertFalse(guard.shouldEnterSafeMode(2 * MINUTE));
        guard.recordBoot(3 * MINUTE);
        assertTrue(guard.shouldEnterSafeMode(3 * MINUTE));
        assertEquals(3, guard.recentBoots(3 * MINUTE));
    }

    @Test
    public void healthyExitClearsTheCounter() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        CrashGuard guard = new CrashGuard(store, 3, 5 * MINUTE);
        guard.recordBoot(0);
        guard.recordBoot(1000);
        guard.recordBoot(2000);
        assertTrue(guard.shouldEnterSafeMode(2000));
        guard.recordHealthyExit(3000);
        assertFalse(guard.shouldEnterSafeMode(4000));
        assertEquals(0, guard.recentBoots(4000));
    }

    @Test
    public void oldBootsFallOutOfTheWindow() {
        CrashGuard guard = new CrashGuard(new MemoryKeyValueStore(), 3, 5 * MINUTE);
        guard.recordBoot(0);
        guard.recordBoot(MINUTE);
        guard.recordBoot(60 * MINUTE);
        assertFalse(guard.shouldEnterSafeMode(60 * MINUTE));
    }

    @Test
    public void corruptStoreDataDoesNotCrash() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        store.putString(CrashGuard.KEY_BOOTS, "not,a,number,,1234");
        CrashGuard guard = new CrashGuard(store);
        assertEquals(0, guard.recentBoots(1));
    }

    @Test
    public void bootHistoryIsBounded() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        CrashGuard guard = new CrashGuard(store, 3, 5 * MINUTE);
        for (int i = 0; i < 50; i++) {
            guard.recordBoot(i);
        }
        String raw = store.getString(CrashGuard.KEY_BOOTS, "");
        assertEquals(8, raw.split(",").length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMeaninglessThreshold() {
        new CrashGuard(new MemoryKeyValueStore(), 1, 1000L);
    }
}
