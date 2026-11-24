package app.gameday.runtime.api;

/** Success-or-failure result for runtime operations. No exceptions cross the runtime boundary. */
public final class RuntimeResult<T> {

    private final boolean ok;
    private final T value;
    private final RuntimeError error;

    private RuntimeResult(boolean ok, T value, RuntimeError error) {
        this.ok = ok;
        this.value = value;
        this.error = error;
    }

    public static <T> RuntimeResult<T> ok(T value) {
        return new RuntimeResult<T>(true, value, null);
    }

    public static RuntimeResult<Void> ok() {
        return new RuntimeResult<Void>(true, null, null);
    }

    public static <T> RuntimeResult<T> error(String code, String message) {
        return new RuntimeResult<T>(false, null, new RuntimeError(code, message));
    }

    public static <T> RuntimeResult<T> error(RuntimeError error) {
        return new RuntimeResult<T>(false, null, error);
    }

    public boolean isOk() {
        return ok;
    }

    public T value() {
        return value;
    }

    public RuntimeError error() {
        return error;
    }

    public String errorCode() {
        return error == null ? "" : error.code();
    }

    public String errorMessage() {
        return error == null ? "" : error.message();
    }
}
