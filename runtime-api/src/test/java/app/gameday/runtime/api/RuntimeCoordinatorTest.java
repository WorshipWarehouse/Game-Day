package app.gameday.runtime.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class RuntimeCoordinatorTest {

    private static class FakeBackend implements RuntimeBackend {
        boolean available = true;
        boolean initOk = true;
        boolean launchOk = true;
        int initCalls;
        int launchCalls;
        boolean shutDown;

        private final RuntimeDescriptor descriptor = new RuntimeDescriptor(
                "fake", "Fake runtime", RuntimeKind.NATIVE_HOST, "1", "test");

        @Override
        public RuntimeDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public RuntimeResult<List<Capability>> initialize() {
            initCalls++;
            if (!initOk) {
                return RuntimeResult.error(RuntimeError.INIT_FAILED, "boom");
            }
            return RuntimeResult.ok(Collections.singletonList(
                    Capability.supported(RuntimeCapability.APP_LAUNCH, "yes")));
        }

        @Override
        public List<Capability> capabilities() {
            return Collections.emptyList();
        }

        @Override
        public CapabilityStatus statusOf(RuntimeCapability capability) {
            return CapabilityStatus.UNKNOWN;
        }

        @Override
        public RuntimeResult<HostAppInfo> hostAppInfo() {
            return RuntimeResult.ok(HostAppInfo.notInstalled("x"));
        }

        @Override
        public RuntimeResult<Void> launch() {
            launchCalls++;
            return launchOk ? RuntimeResult.<Void>ok()
                    : RuntimeResult.<Void>error(RuntimeError.LAUNCH_FAILED, "nope");
        }

        @Override
        public RuntimeResult<Void> stop() {
            return RuntimeResult.ok();
        }

        @Override
        public RuntimeResult<Void> suspend() {
            return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION, "no");
        }

        @Override
        public RuntimeResult<Void> resume() {
            return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION, "no");
        }

        @Override
        public RuntimeResult<Boolean> isHostRunning() {
            return RuntimeResult.ok(Boolean.FALSE);
        }

        @Override
        public RuntimeResult<Void> install(List<String> apkPaths) {
            return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION, "no");
        }

        @Override
        public RuntimeResult<ResourceSnapshot> resources() {
            return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION, "no");
        }

        @Override
        public RuntimeResult<StorageStats> storage() {
            return RuntimeResult.error(RuntimeError.UNSUPPORTED_OPERATION, "no");
        }

        @Override
        public void shutdown() {
            shutDown = true;
        }
    }

    @Test
    public void initializeMovesToReady() {
        RuntimeCoordinator c = new RuntimeCoordinator(new FakeBackend());
        assertEquals(RuntimeState.UNINITIALIZED, c.state());
        assertTrue(c.initialize().isOk());
        assertEquals(RuntimeState.READY, c.state());
        assertTrue(c.history().contains("PROBING"));
    }

    @Test
    public void initializeIsIdempotent() {
        FakeBackend backend = new FakeBackend();
        RuntimeCoordinator c = new RuntimeCoordinator(backend);
        c.initialize();
        c.initialize();
        assertEquals(1, backend.initCalls);
    }

    @Test
    public void unavailableBackendReportsUnavailable() {
        FakeBackend backend = new FakeBackend();
        backend.available = false;
        RuntimeCoordinator c = new RuntimeCoordinator(backend);
        RuntimeResult<Void> r = c.initialize();
        assertFalse(r.isOk());
        assertEquals(RuntimeError.RUNTIME_UNAVAILABLE, r.errorCode());
        assertEquals(RuntimeState.UNAVAILABLE, c.state());
    }

    @Test
    public void failedInitializationIsError() {
        FakeBackend backend = new FakeBackend();
        backend.initOk = false;
        RuntimeCoordinator c = new RuntimeCoordinator(backend);
        assertEquals(RuntimeError.INIT_FAILED, c.initialize().errorCode());
        assertEquals(RuntimeState.ERROR, c.state());
    }

    @Test
    public void launchInitializesThenRuns() {
        FakeBackend backend = new FakeBackend();
        RuntimeCoordinator c = new RuntimeCoordinator(backend);
        assertTrue(c.launchGuest().isOk());
        assertEquals(RuntimeState.RUNNING, c.state());
        assertEquals(1, backend.launchCalls);
    }

    @Test
    public void repeatedLaunchFailuresBlockFurtherAutomaticLaunch() {
        FakeBackend backend = new FakeBackend();
        backend.launchOk = false;
        RuntimeCoordinator c = new RuntimeCoordinator(backend, 3);
        assertFalse(c.launchGuest().isOk());
        assertEquals(RuntimeState.ERROR, c.state());
        assertFalse(c.launchGuest().isOk());
        assertFalse(c.launchGuest().isOk());
        assertEquals(RuntimeState.BLOCKED, c.state());

        RuntimeResult<Void> blocked = c.launchGuest();
        assertFalse(blocked.isOk());
        assertEquals(RuntimeError.BLOCKED_BY_RECOVERY, blocked.errorCode());
        assertEquals("blocked launch must not reach the backend", 3, backend.launchCalls);
    }

    @Test
    public void recoveryClearsTheBlock() {
        FakeBackend backend = new FakeBackend();
        backend.launchOk = false;
        RuntimeCoordinator c = new RuntimeCoordinator(backend, 2);
        c.launchGuest();
        c.launchGuest();
        assertTrue(c.isBlocked());

        backend.launchOk = true;
        c.clearFailures();
        assertEquals(RuntimeState.UNINITIALIZED, c.state());
        assertTrue(c.launchGuest().isOk());
        assertEquals(RuntimeState.RUNNING, c.state());
        assertEquals(0, c.consecutiveFailures());
    }

    @Test
    public void cleanCloseResetsFailureCount() {
        FakeBackend backend = new FakeBackend();
        backend.launchOk = false;
        RuntimeCoordinator c = new RuntimeCoordinator(backend, 3);
        c.launchGuest();
        assertEquals(1, c.consecutiveFailures());
        c.noteGuestClosedCleanly();
        assertEquals(0, c.consecutiveFailures());
    }

    @Test
    public void backgroundingReturnsToReady() {
        RuntimeCoordinator c = new RuntimeCoordinator(new FakeBackend());
        c.launchGuest();
        c.noteGuestBackgrounded();
        assertEquals(RuntimeState.READY, c.state());
    }

    @Test
    public void shutdownReleasesBackend() {
        FakeBackend backend = new FakeBackend();
        RuntimeCoordinator c = new RuntimeCoordinator(backend);
        c.shutdown();
        assertTrue(backend.shutDown);
        assertEquals(RuntimeState.SHUTDOWN, c.state());
    }

    @Test
    public void historyIsBounded() {
        RuntimeCoordinator c = new RuntimeCoordinator(new FakeBackend(), 1);
        for (int i = 0; i < 20; i++) {
            c.initialize();
            c.launchGuest();
            c.clearFailures();
        }
        assertTrue(c.history().size() <= 24);
    }

    @Test
    public void throwingBackendIsConvertedToResult() {
        FakeBackend backend = new FakeBackend() {
            @Override
            public RuntimeResult<Void> launch() {
                throw new IllegalStateException("kaput");
            }
        };
        RuntimeCoordinator c = new RuntimeCoordinator(backend);
        RuntimeResult<Void> r = c.launchGuest();
        assertFalse(r.isOk());
        assertEquals(RuntimeError.INTERNAL_ERROR, r.errorCode());
        assertEquals(RuntimeState.ERROR, c.state());
    }

    @Test
    public void capabilityStatusDefaultsAreHonest() {
        RuntimeBackend backend = new FakeBackend();
        assertEquals(CapabilityStatus.UNKNOWN, backend.statusOf(RuntimeCapability.DRM_PROTECTED_PLAYBACK));
        List<Capability> caps = new ArrayList<Capability>(backend.initialize().value());
        assertEquals(CapabilityStatus.SUPPORTED, caps.get(0).status());
    }
}
