package app.gameday.core.compat;

/** How serious a compatibility finding is. */
public enum IssueSeverity {

    /** Installation or launch must not proceed. */
    BLOCKER(0),

    /** Works, but with a known limitation the user should see. */
    WARNING(1),

    /** Informational, included to make reports self-explanatory. */
    INFO(2);

    private final int rank;

    IssueSeverity(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
