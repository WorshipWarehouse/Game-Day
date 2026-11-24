package app.gameday.host.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.LinearLayout;

import app.gameday.core.input.RemoteKeyMap;
import app.gameday.host.AppServices;
import app.gameday.host.R;

/**
 * Shared scaffolding for GameDay's television screens: fullscreen, dark, D-pad first, with
 * consistent remote shortcuts (MENU opens settings, INFO opens diagnostics, long-press BACK exits
 * GameDay from the home screen).
 */
public abstract class BaseActivity extends Activity {

    protected AppServices services;
    protected LinearLayout body;
    private long backDownAtMs;
    private boolean menuHandled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        services = AppServices.get(this);
        Ui.hideSystemUi(this);
        CharSequence subtitle = subtitleRes() == 0 ? null : getString(subtitleRes());
        body = Ui.beginScreen(this, getString(titleRes()), subtitle);
        build(body);
        focusFirstRow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.hideSystemUi(this);
        onRefresh();
    }

    protected abstract int titleRes();

    /** @return 0 for no subtitle */
    protected abstract int subtitleRes();

    protected abstract void build(LinearLayout body);

    /** Called on every resume so screens reflect state changed elsewhere. */
    protected void onRefresh() {
        // optional
    }

    protected void focusFirstRow() {
        if (body == null || body.getChildCount() == 0) {
            return;
        }
        for (int i = 0; i < body.getChildCount(); i++) {
            if (body.getChildAt(i).isFocusable()) {
                body.getChildAt(i).requestFocus();
                return;
            }
        }
        body.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == RemoteKeyMap.KEYCODE_BACK) {
            backDownAtMs = event.getDownTime();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == RemoteKeyMap.KEYCODE_MENU) {
            if (!menuHandled) {
                menuHandled = true;
                openSettings();
            }
            return true;
        }
        if (keyCode == RemoteKeyMap.KEYCODE_INFO) {
            startActivity(new Intent(this, DiagnosticsActivity.class));
            return true;
        }
        if (keyCode == RemoteKeyMap.KEYCODE_BACK
                && RemoteKeyMap.isLongPress(backDownAtMs, event.getEventTime())) {
            finishAffinity();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        menuHandled = false;
    }

    protected void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    protected String str(int resId) {
        return getString(resId);
    }
}
