package app.gameday.host.diagnostics;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.gameday.core.Redactor;

/**
 * Bounded crash recorder for GameDay's own process.
 *
 * <p>Entries are redacted, truncated and capped so a repeating crash cannot fill the 8 GB device.
 * GameDay cannot observe the guest application's crashes without elevated access; those are reported
 * as "not observable" rather than guessed.
 */
public final class CrashLog {

    private static final String FILE = "crashes.log";
    private static final int MAX_ENTRIES = 16;
    private static final int MAX_ENTRY_CHARS = 4096;

    private final Context context;
    private final Redactor redactor = Redactor.standard();

    public CrashLog(Context context) {
        this.context = context.getApplicationContext();
    }

    public void record(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(buffer);
        writer.println("--- " + app.gameday.core.TimeFormat.isoUtc(System.currentTimeMillis()));
        throwable.printStackTrace(writer);
        writer.flush();
        String text = buffer.toString();
        if (text.length() > MAX_ENTRY_CHARS) {
            text = text.substring(0, MAX_ENTRY_CHARS) + "\n[truncated]";
        }
        append(redactor.redact(text));
    }

    public void record(String message) {
        if (message == null) {
            return;
        }
        String text = app.gameday.core.TimeFormat.isoUtc(System.currentTimeMillis()) + " " + message;
        append(redactor.redact(text));
    }

    private synchronized void append(String entry) {
        List<String> entries = readAll();
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        StringBuilder sb = new StringBuilder();
        for (String e : entries) {
            sb.append(e).append('\n');
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file(), false);
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // A crash log that cannot be written must never crash the app.
        } finally {
            close(out);
        }
    }

    public synchronized List<String> recent() {
        List<String> entries = readAll();
        Collections.reverse(entries);
        return entries;
    }

    public synchronized void clear() {
        File f = file();
        if (f.exists() && !f.delete()) {
            // ignore: worst case the report keeps an old entry
        }
    }

    private List<String> readAll() {
        List<String> out = new ArrayList<String>();
        File f = file();
        if (!f.exists()) {
            return out;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buf.write(chunk, 0, read);
                if (buf.size() > 256 * 1024) {
                    break;
                }
            }
            String[] parts = buf.toString().split("\\n(?=--- )");
            for (String p : parts) {
                String s = p.trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        } catch (IOException ignored) {
            return out;
        } finally {
            close(in);
        }
        return out;
    }

    private File file() {
        return new File(context.getFilesDir(), FILE);
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }

    /** Installs itself as the process-wide handler and delegates to the previous one. */
    public static final class Handler implements Thread.UncaughtExceptionHandler {

        private final CrashLog log;
        private final Thread.UncaughtExceptionHandler previous;

        public Handler(CrashLog log, Thread.UncaughtExceptionHandler previous) {
            this.log = log;
            this.previous = previous;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            try {
                log.record(throwable);
            } catch (RuntimeException ignored) {
                // never mask the original crash
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        }
    }
}
