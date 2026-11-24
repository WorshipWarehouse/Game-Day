package app.gameday.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.gameday.core.input.RemoteAction;
import app.gameday.core.input.RemoteKeyMap;
import org.junit.Test;

public class RemoteKeyMapTest {

    @Test
    public void mapsDpadAndSelect() {
        assertEquals(RemoteAction.UP, RemoteKeyMap.map(RemoteKeyMap.KEYCODE_DPAD_UP));
        assertEquals(RemoteAction.DOWN, RemoteKeyMap.map(RemoteKeyMap.KEYCODE_DPAD_DOWN));
        assertEquals(RemoteAction.LEFT, RemoteKeyMap.map(RemoteKeyMap.KEYCODE_DPAD_LEFT));
        assertEquals(RemoteAction.RIGHT, RemoteKeyMap.map(RemoteKeyMap.KEYCODE_DPAD_RIGHT));
        assertEquals(RemoteAction.SELECT, RemoteKeyMap.map(RemoteKeyMap.KEYCODE_DPAD_CENTER));
        assertEquals(RemoteAction.SELECT, RemoteKeyMap.map(RemoteKeyMap.KEYCODE_ENTER));
        assertEquals(RemoteAction.SELECT, RemoteKeyMap.map(RemoteKeyMap.KEYCODE_BUTTON_A));
    }

    @Test
    public void mapsTransportKeys() {
        assertEquals(RemoteAction.PLAY_PAUSE,
                RemoteKeyMap.map(RemoteKeyMap.KEYCODE_MEDIA_PLAY_PAUSE));
        assertEquals(RemoteAction.FAST_FORWARD,
                RemoteKeyMap.map(RemoteKeyMap.KEYCODE_MEDIA_FAST_FORWARD));
        assertEquals(RemoteAction.REWIND, RemoteKeyMap.map(RemoteKeyMap.KEYCODE_MEDIA_REWIND));
    }

    @Test
    public void unmappedKeysStayUnknownSoPlatformCanHandleThem() {
        assertEquals(RemoteAction.UNKNOWN, RemoteKeyMap.map(-999));
        assertFalse(RemoteKeyMap.shouldHandleRepeat(-999));
    }

    @Test
    public void onlyDirectionsRepeat() {
        assertTrue(RemoteKeyMap.shouldHandleRepeat(RemoteKeyMap.KEYCODE_DPAD_DOWN));
        assertTrue(RemoteKeyMap.shouldHandleRepeat(RemoteKeyMap.KEYCODE_DPAD_RIGHT));
        assertFalse(RemoteKeyMap.shouldHandleRepeat(RemoteKeyMap.KEYCODE_DPAD_CENTER));
        assertFalse(RemoteKeyMap.shouldHandleRepeat(RemoteKeyMap.KEYCODE_MEDIA_PLAY_PAUSE));
        assertFalse(RemoteKeyMap.shouldHandleRepeat(RemoteKeyMap.KEYCODE_BACK));
    }

    @Test
    public void longPressDetectedAtThreshold() {
        long down = 1_000L;
        assertFalse(RemoteKeyMap.isLongPress(down, down + 599));
        assertTrue(RemoteKeyMap.isLongPress(down, down + 600));
        assertFalse(RemoteKeyMap.isLongPress(0, 10_000));
        assertFalse(RemoteKeyMap.isLongPress(5_000, 1_000));
    }

    @Test
    public void transportAndDirectionClassification() {
        assertTrue(RemoteAction.LEFT.isDirectional());
        assertFalse(RemoteAction.SELECT.isDirectional());
        assertTrue(RemoteAction.PLAY_PAUSE.isTransport());
        assertFalse(RemoteAction.MENU.isTransport());
    }
}
