package app.gameday.host.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The TV layout must not move; the touch layout must stay usable at 320 dp and on a tablet. */
public final class FormFactorTest {

    private static final FormFactor FIRE_TV = FormFactor.of(true, 960);
    private static final FormFactor PHONE = FormFactor.of(false, 360);
    private static final FormFactor SMALL_PHONE = FormFactor.of(false, 320);
    private static final FormFactor TABLET = FormFactor.of(false, 800);
    private static final FormFactor LARGE_TABLET = FormFactor.of(false, 1280);

    @Test
    public void televisionKeepsItsOwnGutters() {
        assertEquals(48, FIRE_TV.sidePaddingDp());
        assertEquals(26, FIRE_TV.topPaddingDp());
        assertEquals(56, FIRE_TV.brandTextSizeSp());
    }

    @Test
    public void televisionHidesSystemUiAndFocusesTheFirstRow() {
        assertTrue(FIRE_TV.hidesSystemUi());
        assertTrue(FIRE_TV.focusesFirstRow());
    }

    @Test
    public void touchDeviceLeavesSystemBarsAloneAndDoesNotStealFocus() {
        assertFalse(PHONE.hidesSystemUi());
        assertFalse(PHONE.focusesFirstRow());
        assertFalse(TABLET.hidesSystemUi());
        assertFalse(TABLET.focusesFirstRow());
    }

    @Test
    public void phoneUsesThumbSizedGutters() {
        assertEquals(16, PHONE.sidePaddingDp());
        assertEquals(16, SMALL_PHONE.sidePaddingDp());
        assertEquals(16, PHONE.topPaddingDp());
    }

    @Test
    public void phoneSplashFitsANarrowScreen() {
        assertEquals(44, PHONE.brandTextSizeSp());
    }

    @Test
    public void tabletCentresContentInsteadOfStretchingRows() {
        assertTrue(TABLET.isTablet());
        // 800 dp wide: (800 - 720) / 2 = 40 dp, wider than the 32 dp tablet gutter.
        assertEquals(40, TABLET.sidePaddingDp());
        assertEquals(280, LARGE_TABLET.sidePaddingDp());
        assertEquals(56, TABLET.brandTextSizeSp());
    }

    @Test
    public void narrowTabletStillGetsTheTabletGutter() {
        // 700 dp cannot centre a 720 dp column, so the base gutter applies.
        assertEquals(32, FormFactor.of(false, 700).sidePaddingDp());
    }

    @Test
    public void missingWidthFallsBackToAPhoneWidth() {
        assertEquals(360, FormFactor.of(false, 0).widthDp);
        assertEquals(16, FormFactor.of(false, -1).sidePaddingDp());
    }
}
