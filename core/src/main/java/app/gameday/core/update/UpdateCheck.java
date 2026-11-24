package app.gameday.core.update;

import app.gameday.core.VersionCode;

/** Decides whether a validated release manifest should be offered to this device. */
public final class UpdateCheck {

    private UpdateCheck() {
    }

    public enum Status {
        /** Installed build is current for the selected channel. */
        UP_TO_DATE,
        /** A newer, device-compatible build exists. */
        UPDATE_AVAILABLE,
        /** The release needs a newer Android level than this device provides. */
        INCOMPATIBLE_DEVICE,
        /** The release belongs to a channel the user has not opted into. */
        CHANNEL_NOT_ACCEPTED,
        /** No usable manifest. */
        INVALID
    }

    public static final class Result {
        private final Status status;
        private final UpdateManifest manifest;
        private final String reason;

        public Result(Status status, UpdateManifest manifest, String reason) {
            this.status = status;
            this.manifest = manifest;
            this.reason = reason;
        }

        public Status status() {
            return status;
        }

        public UpdateManifest manifest() {
            return manifest;
        }

        public String reason() {
            return reason;
        }

        public boolean isUpdateAvailable() {
            return status == Status.UPDATE_AVAILABLE;
        }
    }

    public static Result evaluate(UpdateManifest manifest, long installedVersionCode, int deviceSdk,
            ReleaseChannel desiredChannel) {
        if (manifest == null) {
            return new Result(Status.INVALID, null, "No release manifest available.");
        }
        if (!desiredChannel.accepts(manifest.channel())) {
            return new Result(Status.CHANNEL_NOT_ACCEPTED, manifest,
                    "Release " + manifest.versionName() + " is on the "
                            + manifest.channel().id() + " channel.");
        }
        if (manifest.minSdk() > deviceSdk) {
            return new Result(Status.INCOMPATIBLE_DEVICE, manifest,
                    "Release " + manifest.versionName() + " requires Android API "
                            + manifest.minSdk() + "; this device reports API " + deviceSdk + ".");
        }
        if (VersionCode.isUpgrade(installedVersionCode, manifest.versionCode())) {
            return new Result(Status.UPDATE_AVAILABLE, manifest,
                    "Version " + manifest.versionName() + " is available.");
        }
        return new Result(Status.UP_TO_DATE, manifest,
                "Installed version is current (" + manifest.versionName() + ").");
    }
}
