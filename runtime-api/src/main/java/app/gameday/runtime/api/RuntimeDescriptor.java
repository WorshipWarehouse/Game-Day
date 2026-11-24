package app.gameday.runtime.api;

/** Identity and one-line summary of a runtime backend, shown in settings and diagnostics. */
public final class RuntimeDescriptor {

    private final String id;
    private final String displayName;
    private final RuntimeKind kind;
    private final String version;
    private final String summary;

    public RuntimeDescriptor(String id, String displayName, RuntimeKind kind, String version,
            String summary) {
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
        this.version = version;
        this.summary = summary == null ? "" : summary;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public RuntimeKind kind() {
        return kind;
    }

    public String version() {
        return version;
    }

    public String summary() {
        return summary;
    }

    @Override
    public String toString() {
        return displayName + " " + version + " (" + kind + ")";
    }
}
