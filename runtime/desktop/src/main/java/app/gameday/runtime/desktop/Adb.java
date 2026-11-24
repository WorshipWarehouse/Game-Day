package app.gameday.runtime.desktop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The guest control channel.
 *
 * <p>Avo exposes adb, and so does every other desktop Android VM worth building on. Keeping all
 * guest interaction behind this two-method interface is what makes the backend testable without a
 * virtual machine, and what would let the same backend move to a different VMM later.
 */
public interface Adb {

    Result run(String... args);

    final class Result {
        public final boolean ok;
        public final String output;

        public Result(boolean ok, String output) {
            this.ok = ok;
            this.output = output == null ? "" : output;
        }

        public static Result ok(String output) {
            return new Result(true, output);
        }

        public static Result failed(String output) {
            return new Result(false, output);
        }
    }

    /** Runs the real adb binary for one device. */
    final class Process implements Adb {

        private final String adbPath;
        private final String serial;

        public Process(String adbPath, String serial) {
            this.adbPath = adbPath;
            this.serial = serial;
        }

        @Override
        public Result run(String... args) {
            List<String> command = new ArrayList<String>();
            command.add(adbPath);
            if (serial != null && !serial.isEmpty()) {
                command.add("-s");
                command.add(serial);
            }
            command.addAll(Arrays.asList(args));
            java.lang.Process process = null;
            try {
                process = new ProcessBuilder(command).redirectErrorStream(true).start();
                String output = readAll(process.getInputStream());
                int exit = process.waitFor();
                return new Result(exit == 0, output);
            } catch (IOException e) {
                return Result.failed("Could not run adb: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failed("adb was interrupted");
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }

        private static String readAll(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }
}
