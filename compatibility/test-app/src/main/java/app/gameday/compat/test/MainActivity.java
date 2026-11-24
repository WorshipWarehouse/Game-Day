package app.gameday.compat.test;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import app.gameday.core.Json;
import app.gameday.core.Sha256;

/**
 * Self-owned instrument for validating a runtime on real hardware.
 *
 * <p>Deliberately independent of the GameDay launcher: it has its own package, UID and signature, so
 * running it proves that two applications coexist and that GameDay is not faking any measurement.
 * Every result is produced from a real API call on the device - none of it is simulated.
 *
 * <p>The video result depends on a test clip the operator supplies at
 * {@code Android/data/app.gameday.compat.test/files/test.mp4}; no content is bundled with the app.
 */
public final class MainActivity extends Activity {

    private static final int ACCENT = 0xFF2BD46B;
    private static final int TEXT = 0xFFF3F6F9;
    private static final int MUTED = 0xFF9BA8B5;
    private static final int DANGER = 0xFFF05252;

    private final List<TestResult> results = new ArrayList<TestResult>();
    private LinearLayout resultList;
    private TextView summaryView;
    private int keyEvents;
    private int touchEvents;
    private int lastKeyCode = -1;
    private long createdAt;
    private long framesDrawn;
    private long droppedFrames;

    private static final class TestResult {
        final String name;
        final boolean ok;
        final String detail;

        TestResult(String name, boolean ok, String detail) {
            this.name = name;
            this.ok = ok;
            this.detail = detail;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createdAt = SystemClock.elapsedRealtime();
        setContentView(buildUi());
        runAllTests();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0E13);
        root.setPadding(dp(28), dp(22), dp(28), dp(18));

        TextView title = new TextView(this);
        title.setText("GameDay compatibility test");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        root.addView(title);

        summaryView = new TextView(this);
        summaryView.setTextColor(MUTED);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        root.addView(summaryView);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, dp(10));

        Button run = new Button(this);
        run.setText("Run tests");
        run.setOnClickListener(v -> runAllTests());
        actions.addView(run);

        Button crash = new Button(this);
        crash.setText("Crash this app");
        crash.setOnClickListener(v -> {
            throw new RuntimeException("GameDay compatibility crash test");
        });
        actions.addView(crash);
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        resultList = new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(resultList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void runAllTests() {
        results.clear();
        resultList.removeAllViews();
        summaryView.setText("Running…");

        add(isolatedIdentity());
        add(storagePersistence());
        add(audioPlayback());
        add(codecCapabilities());
        add(drmCapabilities());
        add(videoPlayback());

        measureFrames();
        measureResources();
        measureNetwork();
    }

    private void add(TestResult result) {
        results.add(result);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView name = new TextView(this);
        name.setText((result.ok ? "PASS  " : "FAIL  ") + result.name);
        name.setTextColor(result.ok ? ACCENT : DANGER);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        row.addView(name);

        TextView detail = new TextView(this);
        detail.setText(result.detail);
        detail.setTextColor(MUTED);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        row.addView(detail);
        resultList.addView(row);
    }

    private TestResult isolatedIdentity() {
        int uid = Process.myUid();
        String signer = "unknown";
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
            if (info.signatures != null && info.signatures.length > 0) {
                signer = Sha256.of(info.signatures[0].toByteArray()).substring(0, 16);
            }
        } catch (Exception e) {
            signer = "unreadable: " + e.getMessage();
        }
        String detail = "package=" + getPackageName() + " uid=" + uid + " signer=" + signer
                + " model=" + Build.MODEL + " api=" + Build.VERSION.SDK_INT
                + " abis=" + String.join(",", Build.SUPPORTED_ABIS)
                + " startLatencyMs=" + (SystemClock.elapsedRealtime() - createdAt);
        return new TestResult("Isolated identity", true, detail);
    }

    private TestResult storagePersistence() {
        File counter = new File(getFilesDir(), "persist.txt");
        int previous = 0;
        try {
            if (counter.exists()) {
                FileInputStream in = new FileInputStream(counter);
                byte[] buf = new byte[64];
                int read = in.read(buf);
                in.close();
                if (read > 0) {
                    previous = Integer.parseInt(new String(buf, 0, read).trim());
                }
            }
            FileOutputStream out = new FileOutputStream(counter, false);
            out.write(String.valueOf(previous + 1).getBytes("UTF-8"));
            out.close();
            String readBack = new String(java.nio.file.Files.readAllBytes(counter.toPath()), "UTF-8");
            boolean ok = readBack.trim().equals(String.valueOf(previous + 1));
            return new TestResult("Persistent storage", ok,
                    "counter " + previous + " -> " + readBack.trim() + " in " + counter.getParent());
        } catch (Exception e) {
            return new TestResult("Persistent storage", false, "error: " + e.getMessage());
        }
    }

    private TestResult audioPlayback() {
        AudioTrack track = null;
        try {
            int sampleRate = 44100;
            int samples = sampleRate / 4;
            short[] pcm = new short[samples];
            for (int i = 0; i < samples; i++) {
                pcm[i] = (short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 8000);
            }
            int bytes = pcm.length * 2;
            track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bytes,
                    AudioTrack.MODE_STATIC);
            int written = track.write(pcm, 0, pcm.length);
            track.play();
            Thread.sleep(220);
            return new TestResult("Audio playback", written > 0,
                    "wrote " + written + " samples, state " + track.getPlayState()
                            + ", sampleRate " + sampleRate);
        } catch (Exception e) {
            return new TestResult("Audio playback", false, "error: " + e.getMessage());
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (Exception ignored) {
                    // already stopped
                }
                track.release();
            }
        }
    }

    private TestResult codecCapabilities() {
        StringBuilder sb = new StringBuilder();
        boolean hwAvc = false;
        boolean hwHevc = false;
        boolean secure = false;
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                boolean hardware = isHardware(info);
                for (String type : info.getSupportedTypes()) {
                    if ("video/avc".equalsIgnoreCase(type) && hardware) {
                        hwAvc = true;
                    }
                    if ("video/hevc".equalsIgnoreCase(type) && hardware) {
                        hwHevc = true;
                    }
                    if (("video/avc".equalsIgnoreCase(type) || "video/hevc".equalsIgnoreCase(type))
                            && hardware) {
                        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                        if (caps != null && (caps.isFeatureSupported(
                                MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback)
                                || info.getName().toLowerCase(Locale.US).contains(".secure"))) {
                            secure = true;
                        }
                    }
                }
            }
            sb.append("hardwareAvc=").append(hwAvc).append(" hardwareHevc=").append(hwHevc)
                    .append(" secureDecoder=").append(secure);
        } catch (Exception e) {
            return new TestResult("Video decoders", false, "error: " + e.getMessage());
        }
        return new TestResult("Video decoders", hwAvc || hwHevc, sb.toString());
    }

    private static boolean isHardware(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                if (!info.isHardwareAccelerated() || info.isSoftwareOnly()) {
                    return false;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        String name = info.getName().toLowerCase(Locale.US);
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.")
                || name.contains(".sw.") || name.contains("software"));
    }

    private TestResult drmCapabilities() {
        try {
            boolean widevine = MediaDrm.isCryptoSchemeSupported(
                    UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"));
            return new TestResult("DRM (Widevine)", widevine,
                    "MediaDrm widevine supported=" + widevine);
        } catch (Exception e) {
            return new TestResult("DRM (Widevine)", false, "error: " + e.getMessage());
        }
    }

    private TestResult videoPlayback() {
        File clip = new File(getExternalFilesDir(null), "test.mp4");
        if (!clip.isFile()) {
            return new TestResult("Unprotected video playback", false,
                    "no test clip supplied at " + clip.getAbsolutePath()
                            + " (adb push a short H.264/HEVC file there and re-run)");
        }
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(clip.getAbsolutePath());
            player.prepare();
            int duration = player.getDuration();
            int width = player.getVideoWidth();
            int height = player.getVideoHeight();
            player.start();
            Thread.sleep(1200);
            boolean playing = player.isPlaying();
            player.stop();
            return new TestResult("Unprotected video playback", duration > 0 && width > 0,
                    "durationMs=" + duration + " size=" + width + "x" + height
                            + " playingAfter1s=" + playing);
        } catch (Exception e) {
            return new TestResult("Unprotected video playback", false, "error: " + e.getMessage());
        } finally {
            player.release();
        }
    }

    /** Counts Choreographer callbacks for two seconds while a view invalidates itself. */
    private void measureFrames() {
        final View probe = new View(this);
        probe.setBackgroundColor(0xFF000000);
        resultList.addView(probe, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        framesDrawn = 0;
        droppedFrames = 0;
        final long start = SystemClock.elapsedRealtime();
        final long[] last = {start};
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                long now = SystemClock.elapsedRealtime();
                long delta = now - last[0];
                last[0] = now;
                framesDrawn++;
                if (delta > 25) {
                    droppedFrames++;
                }
                probe.invalidate();
                if (now - start < 2000) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    double seconds = Math.max(0.001, (now - start) / 1000.0);
                    double fps = framesDrawn / seconds;
                    add(new TestResult("Rendering", fps > 30,
                            String.format(Locale.US, "%.1f fps over %.2fs, %d frames slower than "
                                    + "25ms", Double.valueOf(fps), Double.valueOf(seconds),
                                    Long.valueOf(droppedFrames))));
                }
            }
        });
    }

    private void measureResources() {
        Debug.MemoryInfo info = new Debug.MemoryInfo();
        Debug.getMemoryInfo(info);
        int passed = 0;
        for (TestResult r : results) {
            if (r.ok) {
                passed++;
            }
        }
        summaryView.setText("PSS " + (info.getTotalPss() / 1024) + " MB • " + passed + "/"
                + results.size() + " checks passed • keys " + keyEvents + " • touches "
                + touchEvents + " • lastKey " + lastKeyCode + " • "
                + reportFile().getAbsolutePath());
    }

    private void measureNetwork() {
        new Thread(() -> {
            TestResult result;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://api.github.com/zen")
                        .openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "GameDay-CompatTest/1.0");
                int code = connection.getResponseCode();
                result = new TestResult("Networking", code == 200,
                        "HTTP " + code + " from api.github.com");
            } catch (Exception e) {
                result = new TestResult("Networking", false, "network error: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final TestResult finalResult = result;
            runOnUiThread(() -> {
                add(finalResult);
                writeReport();
                measureResources();
            });
        }, "compat-network").start();
    }

    private void writeReport() {
        Json root = Json.object()
                .put("schemaVersion", 1)
                .put("kind", "gameday-compat-test")
                .put("packageName", getPackageName())
                .put("model", Build.MODEL)
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("startLatencyMs", SystemClock.elapsedRealtime() - createdAt);
        Json tests = Json.array();
        for (TestResult r : results) {
            tests.add(Json.object().put("name", r.name).put("ok", r.ok).put("detail", r.detail));
        }
        root.put("tests", tests);
        try {
            FileOutputStream out = new FileOutputStream(reportFile(), false);
            out.write(root.toJson().getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
            // the on-screen results remain authoritative
        }
    }

    private File reportFile() {
        return new File(getExternalFilesDir(null), "results.json");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            keyEvents++;
            lastKeyCode = event.getKeyCode();
            measureResources();
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            touchEvents++;
            measureResources();
        }
        return super.dispatchTouchEvent(event);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }
}
