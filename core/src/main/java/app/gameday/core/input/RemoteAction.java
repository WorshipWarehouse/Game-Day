package app.gameday.core.input;

/** Logical actions the Fire TV remote maps onto. */
public enum RemoteAction {

    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACK,
    MENU,
    INFO,
    PLAY_PAUSE,
    PLAY,
    PAUSE,
    FAST_FORWARD,
    REWIND,
    NEXT,
    PREVIOUS,
    SETTINGS,
    SEARCH,
    UNKNOWN;

    public boolean isDirectional() {
        return this == UP || this == DOWN || this == LEFT || this == RIGHT;
    }

    public boolean isTransport() {
        return this == PLAY_PAUSE || this == PLAY || this == PAUSE
                || this == FAST_FORWARD || this == REWIND || this == NEXT || this == PREVIOUS;
    }

    /** Whether holding the key may legitimately repeat (seek, scroll) without breaking navigation. */
    public boolean allowsRepeat() {
        return this == UP || this == DOWN || this == LEFT || this == RIGHT;
    }
}
