package app.gameday.core.update;

/** Release channels GameDay can follow. */
public enum ReleaseChannel {

    /** Default: only marked stable releases. */
    STABLE("stable"),

    /** Opt-in: prereleases from the project's own release feed. */
    BETA("beta");

    private final String id;

    ReleaseChannel(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ReleaseChannel fromId(String id) {
        if (id != null) {
            String s = id.trim().toLowerCase(java.util.Locale.US);
            for (ReleaseChannel c : values()) {
                if (c.id.equals(s)) {
                    return c;
                }
            }
        }
        return STABLE;
    }

    /** Whether a release published on {@code releaseChannel} is visible to {@code this} channel. */
    public boolean accepts(ReleaseChannel releaseChannel) {
        if (releaseChannel == null) {
            return false;
        }
        return this == BETA || releaseChannel == STABLE;
    }
}
