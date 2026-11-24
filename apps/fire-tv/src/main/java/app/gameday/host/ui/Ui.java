package app.gameday.host.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The whole visual language of GameDay, in code.
 *
 * <p>Native views only, no AndroidX and no layout inflation: a launcher that must appear in under
 * three seconds on a 1.5 GB device should not spend time parsing XML or loading support libraries.
 * Every control is a focusable row with a visible focus ring, sized for a television at three
 * metres, and reachable with D-pad only.
 */
public final class Ui {

    public static final int BG = 0xFF0A0E13;
    public static final int SURFACE = 0xFF141B24;
    public static final int SURFACE_ALT = 0xFF1B2531;
    public static final int FOCUS = 0xFF1F3A2B;
    public static final int ACCENT = 0xFF2BD46B;
    public static final int TEXT = 0xFFF3F6F9;
    public static final int MUTED = 0xFF9BA8B5;
    public static final int DANGER = 0xFFF05252;
    public static final int WARNING = 0xFFF2B33D;
    public static final int DIVIDER = 0xFF22303D;

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    /**
     * Applies the window mode this device needs: immersive on a television, ordinary system bars on
     * a phone or tablet, where a hidden status bar would only cost the user their navigation.
     */
    public static void applyWindowMode(Activity activity, FormFactor form) {
        if (form.hidesSystemUi()) {
            hideSystemUi(activity);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    public static void hideSystemUi(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    public static TextView title(Context context, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView heading(Context context, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView body(Context context, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        view.setLineSpacing(dp(context, 4), 1f);
        return view;
    }

    public static TextView caption(Context context, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(MUTED);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    public static TextView badge(Context context, CharSequence text, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE_ALT);
        bg.setCornerRadius(dp(context, 6));
        bg.setStroke(dp(context, 1), color);
        view.setBackground(bg);
        view.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        return view;
    }

    public static TextView sectionHeader(Context context, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(ACCENT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setAllCaps(true);
        view.setLetterSpacing(0.08f);
        view.setPadding(0, dp(context, 22), 0, dp(context, 8));
        return view;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        params.topMargin = dp(context, 10);
        params.bottomMargin = dp(context, 10);
        view.setLayoutParams(params);
        return view;
    }

    public static void space(LinearLayout parent, int heightDp) {
        View spacer = new View(parent.getContext());
        parent.addView(spacer,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(parent.getContext(), heightDp)));
    }

    /** A focusable menu row: accent bar, title, optional subtitle. */
    public static final class MenuRow {
        public final LinearLayout view;
        public final TextView titleView;
        public final TextView subtitleView;
        private final View indicator;

        MenuRow(LinearLayout view, TextView titleView, TextView subtitleView, View indicator) {
            this.view = view;
            this.titleView = titleView;
            this.subtitleView = subtitleView;
            this.indicator = indicator;
        }

        public MenuRow subtitle(CharSequence text) {
            subtitleView.setText(text);
            subtitleView.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
            return this;
        }

        public MenuRow onClick(View.OnClickListener listener) {
            view.setOnClickListener(listener);
            return this;
        }

        public MenuRow enabled(boolean enabled) {
            view.setEnabled(enabled);
            view.setFocusable(enabled);
            view.setAlpha(enabled ? 1f : 0.45f);
            return this;
        }

        public void focus() {
            view.requestFocus();
        }
    }

    public static MenuRow menuRow(Context context, CharSequence title, CharSequence subtitle) {
        LinearLayout row = horizontal(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setFocusable(true);
        row.setFocusableInTouchMode(false);
        row.setClickable(true);
        row.setBackground(rowBackground(context));
        row.setPadding(dp(context, 12), dp(context, 12), dp(context, 16), dp(context, 12));
        // 48 dp is the documented minimum touch target; a television row is already taller, so this
        // only changes the phone and tablet layout.
        row.setMinimumHeight(dp(context, 48));

        View indicator = new View(context);
        LinearLayout.LayoutParams indicatorParams =
                new LinearLayout.LayoutParams(dp(context, 4), ViewGroup.LayoutParams.MATCH_PARENT);
        indicatorParams.rightMargin = dp(context, 14);
        indicator.setLayoutParams(indicatorParams);
        indicator.setBackgroundColor(0x00000000);
        row.addView(indicator);

        LinearLayout textColumn = column(context);
        TextView titleView = heading(context, title);
        TextView subtitleView = caption(context, subtitle == null ? "" : subtitle);
        subtitleView.setVisibility(subtitle == null || subtitle.length() == 0
                ? View.GONE : View.VISIBLE);
        textColumn.addView(titleView);
        textColumn.addView(subtitleView);
        row.addView(textColumn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final MenuRow result = new MenuRow(row, titleView, subtitleView, indicator);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            indicator.setBackgroundColor(hasFocus ? ACCENT : 0x00000000);
            titleView.setTextColor(hasFocus ? ACCENT : TEXT);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 8);
        row.setLayoutParams(params);
        return result;
    }

    /** A non-focusable label/value row for reports and settings summaries. */
    public static LinearLayout valueRow(Context context, CharSequence label, CharSequence value) {
        LinearLayout row = horizontal(context);
        row.setPadding(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 6));
        TextView labelView = caption(context, label);
        labelView.setSingleLine(false);
        TextView valueView = body(context, value == null ? "" : value);
        valueView.setGravity(Gravity.END);
        valueView.setTextColor(TEXT);
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams valueParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(labelView, labelParams);
        row.addView(valueView, valueParams);
        return row;
    }

    /** A status row with a coloured result chip. */
    public static LinearLayout statusRow(Context context, CharSequence label, CharSequence message,
            boolean ok) {
        LinearLayout row = horizontal(context);
        row.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 8));
        TextView chip = badge(context, ok ? "OK" : "CHECK", ok ? ACCENT : WARNING);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.rightMargin = dp(context, 12);
        row.addView(chip, chipParams);

        LinearLayout text = column(context);
        text.addView(body(context, label));
        if (message != null && message.length() > 0) {
            text.addView(caption(context, message));
        }
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private static Drawable rowBackground(Context context) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(SURFACE);
        normal.setCornerRadius(dp(context, 8));

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(FOCUS);
        focused.setCornerRadius(dp(context, 8));
        focused.setStroke(dp(context, 2), ACCENT);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    /**
     * Installs the standard scaffold: brand header, scrollable body, footer, and returns the body
     * container rows are added to.
     */
    public static LinearLayout beginScreen(Activity activity, CharSequence title,
            CharSequence subtitle) {
        FormFactor form = FormFactor.detect(activity);
        LinearLayout root = column(activity);
        root.setBackgroundColor(BG);
        int side = dp(activity, form.sidePaddingDp());
        root.setPadding(side, dp(activity, form.topPaddingDp()), side, dp(activity, 18));

        LinearLayout header = column(activity);
        header.addView(title(activity, title));
        if (subtitle != null && subtitle.length() > 0) {
            header.addView(caption(activity, subtitle));
        }
        root.addView(header);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = column(activity);
        body.setPadding(0, dp(activity, 18), 0, dp(activity, 18));
        scroll.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout.LayoutParams bodyParams =
                (LinearLayout.LayoutParams) scroll.getLayoutParams();
        bodyParams.weight = 1f;
        scroll.setLayoutParams(bodyParams);

        activity.setContentView(root);
        return body;
    }

    /** Runs work off the UI thread; GameDay has no background services and no executors to pool. */
    public static void background(Runnable runnable) {
        Thread thread = new Thread(runnable, "gameday-bg");
        thread.setDaemon(true);
        thread.start();
    }

    public static void toast(Activity activity, String message) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show();
    }
}
