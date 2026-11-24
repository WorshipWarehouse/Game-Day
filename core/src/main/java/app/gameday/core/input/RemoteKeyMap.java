package app.gameday.core.input;

/**
 * Maps Android key codes to {@link RemoteAction} logically, without depending on the Android
 * framework, so remote navigation rules can be unit tested on any machine.
 *
 * <p>Only keys the Fire TV remote and common Bluetooth remotes actually send are mapped. Unmapped
 * keys fall through to the platform rather than being swallowed, because guest applications may
 * handle extra keys themselves.
 */
public final class RemoteKeyMap {

    // android.view.KeyEvent constants, duplicated to keep this module framework-free.
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_MENU = 82;
    public static final int KEYCODE_SEARCH = 84;
    public static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    public static final int KEYCODE_MEDIA_NEXT = 87;
    public static final int KEYCODE_MEDIA_PREVIOUS = 88;
    public static final int KEYCODE_MEDIA_REWIND = 89;
    public static final int KEYCODE_MEDIA_FAST_FORWARD = 90;
    public static final int KEYCODE_MEDIA_PLAY = 126;
    public static final int KEYCODE_MEDIA_PAUSE = 127;
    public static final int KEYCODE_INFO = 165;
    public static final int KEYCODE_SETTINGS = 176;
    public static final int KEYCODE_BUTTON_A = 96;
    public static final int KEYCODE_SPACE = 62;

    /** Long-press threshold used by the GameDay settings and recovery screens. */
    public static final long LONG_PRESS_MS = 600L;

    private RemoteKeyMap() {
    }

    public static RemoteAction map(int keyCode) {
        switch (keyCode) {
            case KEYCODE_DPAD_UP:
                return RemoteAction.UP;
            case KEYCODE_DPAD_DOWN:
                return RemoteAction.DOWN;
            case KEYCODE_DPAD_LEFT:
                return RemoteAction.LEFT;
            case KEYCODE_DPAD_RIGHT:
                return RemoteAction.RIGHT;
            case KEYCODE_DPAD_CENTER:
            case KEYCODE_ENTER:
            case KEYCODE_BUTTON_A:
            case KEYCODE_SPACE:
                return RemoteAction.SELECT;
            case KEYCODE_BACK:
                return RemoteAction.BACK;
            case KEYCODE_MENU:
                return RemoteAction.MENU;
            case KEYCODE_INFO:
                return RemoteAction.INFO;
            case KEYCODE_MEDIA_PLAY_PAUSE:
                return RemoteAction.PLAY_PAUSE;
            case KEYCODE_MEDIA_PLAY:
                return RemoteAction.PLAY;
            case KEYCODE_MEDIA_PAUSE:
                return RemoteAction.PAUSE;
            case KEYCODE_MEDIA_FAST_FORWARD:
                return RemoteAction.FAST_FORWARD;
            case KEYCODE_MEDIA_REWIND:
                return RemoteAction.REWIND;
            case KEYCODE_MEDIA_NEXT:
                return RemoteAction.NEXT;
            case KEYCODE_MEDIA_PREVIOUS:
                return RemoteAction.PREVIOUS;
            case KEYCODE_SETTINGS:
                return RemoteAction.SETTINGS;
            case KEYCODE_SEARCH:
                return RemoteAction.SEARCH;
            default:
                return RemoteAction.UNKNOWN;
        }
    }

    /**
     * Whether a repeat event from the platform should be acted on. Directional repeats are honoured
     * (holding down scrolls a list); action repeats are dropped so a held SELECT cannot fire twice.
     */
    public static boolean shouldHandleRepeat(int keyCode) {
        return map(keyCode).allowsRepeat();
    }

    public static boolean isLongPress(long downTimeMs, long upTimeMs) {
        return downTimeMs > 0 && upTimeMs >= downTimeMs
                && (upTimeMs - downTimeMs) >= LONG_PRESS_MS;
    }
}
