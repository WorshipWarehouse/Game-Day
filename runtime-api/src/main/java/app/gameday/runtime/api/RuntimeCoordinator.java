package app.gameday.runtime.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Drives a {@link RuntimeBackend} through its lifecycle and prevents launch loops.
 *
 * <p>The UI talks to the coordinator, never to a backend directly, so every path shares the same
 * state machine: initialize, launch, block after repeated failures, recover. Keeping it free of
 * Android types lets it be tested exhaustively on the JVM, which matters because "the app restarts
 * forever" is the worst failure mode a launcher can have.
 */
public final class RuntimeCoordinator {

    private static final int HISTORY_LIMIT = 24;

    private final RuntimeBackend backend;
    private final int maxConsecutiveFailures;
    private final Deque<String> history = new ArrayDeque<String>();

    private RuntimeState state = RuntimeState.UNINITIALIZED;
    private int consecutiveFailures;

    public RuntimeCoordinator(RuntimeBackend backend) {
        this(backend, 3);
    }

    public RuntimeCoordinator(RuntimeBackend backend, int maxConsecutiveFailures) {
        if (backend == null) {
            throw new IllegalArgumentException("backend == null");
        }
        if (maxConsecutiveFailures < 1) {
            throw new IllegalArgumentException("maxConsecutiveFailures must be >= 1");
        }
        this.backend = backend;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public RuntimeBackend backend() {
        return backend;
    }

    public RuntimeState state() {
        return state;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    public boolean isBlocked() {
        return state == RuntimeState.BLOCKED;
    }

    public RuntimeResult<Void> initialize() {
        if (state == RuntimeState.READY || state == RuntimeState.RUNNING) {
            return RuntimeResult.ok();
        }
        transition(RuntimeState.PROBING);
        if (!backend.isAvailable()) {
            transition(RuntimeState.UNAVAILABLE);
            return RuntimeResult.error(RuntimeError.RUNTIME_UNAVAILABLE,
                    backend.descriptor().displayName() + " is not available on this device.");
        }
        RuntimeResult<List<Capability>> init = backend.initialize();
        if (!init.isOk()) {
            transition(RuntimeState.ERROR);
            return RuntimeResult.error(RuntimeError.INIT_FAILED, init.errorMessage());
        }
        transition(RuntimeState.READY);
        return RuntimeResult.ok();
    }

    /**
     * Launches the guest application.
     *
     * <p>After {@code maxConsecutiveFailures} failures the coordinator moves to BLOCKED and refuses
     * further automatic launches: the caller must run recovery (clear temporary files, restart the
     * runtime, or reset configuration) which calls {@link #clearFailures()}.
     */
    public RuntimeResult<Void> launchGuest() {
        if (state == RuntimeState.BLOCKED) {
            return RuntimeResult.error(RuntimeError.BLOCKED_BY_RECOVERY,
                    "Automatic launch is paused after " + consecutiveFailures
                            + " consecutive failures. Open Recovery to continue.");
        }
        if (!state.canLaunch()) {
            RuntimeResult<Void> init = initialize();
            if (!init.isOk()) {
                return init;
            }
        }
        transition(RuntimeState.LAUNCHING);
        RuntimeResult<Void> result;
        try {
            result = backend.launch();
        } catch (RuntimeException e) {
            result = RuntimeResult.error(RuntimeError.INTERNAL_ERROR,
                    "Launch threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (result.isOk()) {
            consecutiveFailures = 0;
            transition(RuntimeState.RUNNING);
        } else {
            consecutiveFailures++;
            transition(consecutiveFailures >= maxConsecutiveFailures
                    ? RuntimeState.BLOCKED : RuntimeState.ERROR);
        }
        return result;
    }

    /** The guest left the foreground on its own (Home pressed, user exited). */
    public void noteGuestBackgrounded() {
        if (state == RuntimeState.RUNNING) {
            transition(RuntimeState.READY);
        }
    }

    /** The guest exited and the user is back in GameDay: a healthy outcome. */
    public void noteGuestClosedCleanly() {
        consecutiveFailures = 0;
        if (state == RuntimeState.RUNNING || state == RuntimeState.ERROR) {
            transition(RuntimeState.READY);
        }
    }

    public RuntimeResult<Void> stopGuest() {
        RuntimeResult<Void> r = backend.stop();
        if (r.isOk()) {
            transition(RuntimeState.READY);
        }
        return r;
    }

    /** Clears failure bookkeeping after a successful recovery action. */
    public void clearFailures() {
        consecutiveFailures = 0;
        if (state == RuntimeState.BLOCKED || state == RuntimeState.ERROR) {
            transition(RuntimeState.UNINITIALIZED);
        }
    }

    public void shutdown() {
        backend.shutdown();
        transition(RuntimeState.SHUTDOWN);
    }

    public List<String> history() {
        return Collections.unmodifiableList(new ArrayList<String>(history));
    }

    private void transition(RuntimeState next) {
        state = next;
        history.addLast(next.name());
        while (history.size() > HISTORY_LIMIT) {
            history.removeFirst();
        }
    }
}
