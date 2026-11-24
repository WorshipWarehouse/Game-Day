package app.gameday.host.ui;

import android.content.Context;

import app.gameday.host.diagnostics.DeviceProbe;

/**
 * The same APK is installed on a Fire TV watched from three metres and on a phone held in one hand.
 * Every measurement that differs between those two situations lives here instead of being repeated
 * in each screen.
 *
 * <p>A television keeps the layout it has always had: wide 48 dp gutters, a hidden status bar, and
 * focus placed on the first row so the remote has somewhere to go. A phone or tablet gets thumb-sized
 * gutters, the system bars left alone, no focus ring until the user actually presses a key or D-pad,
 * and content centred once the screen is wide enough that full-bleed rows would read badly.
 */
public final class FormFactor {

    private static final int TELEVISION_SIDE_DP = 48;
    private static final int TELEVISION_TOP_DP = 26;
    private static final int PHONE_SIDE_DP = 16;
    private static final int TABLET_SIDE_DP = 32;
    private static final int TOUCH_TOP_DP = 16;
    private static final int TABLET_MIN_WIDTH_DP = 600;
    private static final int CONTENT_MAX_WIDTH_DP = 720;

    /** True when the device reports itself as a television (UiModeManager or the leanback feature). */
    public final boolean television;
    /** Current {@code screenWidthDp}; used only for gutters and centring, never for touch decisions. */
    public final int widthDp;

    private FormFactor(boolean television, int widthDp) {
        this.television = television;
        this.widthDp = widthDp;
    }

    public static FormFactor of(boolean television, int widthDp) {
        return new FormFactor(television, widthDp <= 0 ? 360 : widthDp);
    }

    public static FormFactor detect(Context context) {
        int width = context.getResources().getConfiguration().screenWidthDp;
        return of(DeviceProbe.isTelevision(context), width);
    }

    /** GameDay owns the whole screen on a TV; on touch devices the system bars stay reachable. */
    public boolean hidesSystemUi() {
        return television;
    }

    /**
     * A remote has no pointer, so the first row must already be focused when a screen opens. Touch
     * devices should not start with a focus ring drawn on something the user did not select.
     */
    public boolean focusesFirstRow() {
        return television;
    }

    public boolean isTablet() {
        return widthDp >= TABLET_MIN_WIDTH_DP;
    }

    /**
     * Horizontal gutter. On a wide touch screen the extra space becomes a centred content column
     * rather than ever-longer rows, which is what makes the same screens readable on a tablet.
     */
    public int sidePaddingDp() {
        if (television) {
            return TELEVISION_SIDE_DP;
        }
        int base = isTablet() ? TABLET_SIDE_DP : PHONE_SIDE_DP;
        int centred = (widthDp - CONTENT_MAX_WIDTH_DP) / 2;
        return Math.max(base, centred);
    }

    public int topPaddingDp() {
        return television ? TELEVISION_TOP_DP : TOUCH_TOP_DP;
    }

    /** The splash wordmark is set for a television; a phone needs it to fit a 360 dp width. */
    public int brandTextSizeSp() {
        return television || isTablet() ? 56 : 44;
    }
}
