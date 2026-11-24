# ADR 0001 — Runtime selection: native hosting on Fire OS

- **Status:** accepted
- **Date:** 2026-11-24
- **Deciders:** GameDay maintainers
- **Supersedes:** none

## Context

GameDay must give a Fire TV Stick 4K (1st gen, `AFTMM`, Fire OS 6, Android 7.1 / API 25, 32-bit,
1.5 GB RAM, no root) a seamless way to watch the official NFL application's authorized content.

Constraints that cannot be negotiated:

- No root, no unlocked bootloader, no system signatures.
- No circumvention of DRM, attestation, integrity checks or content restrictions; no modified or
  re-signed NFL packages; no invented update APIs.
- Hardware-accelerated decode must be real and verifiable, not claimed.
- The host's own memory overhead should stay far below 100 MB.
- Dependencies must be legally redistributable under an open-source licence.

## Options considered

1. **Native host** — launch the Amazon Appstore build of `com.gotv.nflgamecenter.us.lite` in place.
2. **Application virtualization** — embed a VirtualApp-class engine and run the guest inside
   GameDay's process.
3. **Android container (Waydroid)** — run a full Android userspace in containers.
4. **Android Virtualization Framework** — run Android in a pVM via pKVM/crosvm.
5. **Full-system emulation (QEMU/Limbo)** — emulate a whole Android device in software.

Evidence for each is in [../RESEARCH.md](../RESEARCH.md).

## Decision

**Adopt native hosting as the production runtime.** GameDay is a managed launcher, update and
diagnostics layer around the Appstore-installed NFL app. No guest code runs inside GameDay's process.

The runtime abstraction (`runtime-api`) still models other kinds — `APP_VIRTUALIZATION`,
`SYSTEM_EMULATION` — so a future, genuinely viable backend can be added without changing the UI, and
so capability reports can state why each was rejected.

## Rationale

- **It is the only option that keeps protected playback on the supported path.** The guest keeps its
  own UID, signature, `MediaDrm` session, secure decoder and surface. Hardware decode and HDCP 2.2
  are the platform's normal behaviour, not something GameDay emulates.
- **The guest already supports the target.** `minSdk 25`, `armeabi-v7a` present, leanback launcher,
  and NFL+ lists Fire TV 6+ as supported.
- **The alternatives are blocked by facts, not preferences**:
  - VirtualApp's public code has **no licence** (all rights reserved) and has been frozen since
    December 2017; hosting guests under the host's UID also weakens isolation and invites
    virtualization detection.
  - Waydroid needs GNU/Linux, root and kernel binder support.
  - AVF needs Android 13+, ARM64 and a system permission.
  - QEMU/Limbo cannot deliver hardware-decoded, DRM-protected 1080p on a 1.7 GHz ARMv7 SoC.
- **It meets the footprint target.** The shipped APK is 112 KB with no native libraries.

## Consequences

**Positive**

- Real hardware decode, real DRM, real sign-in, no emulation overhead.
- Small, auditable codebase with a pure-JVM core that is fully unit tested.
- Honest capability reporting: what GameDay cannot do (input injection, silent install, guest crash
  capture) is reported as unsupported with a reason.

**Negative / accepted limitations**

- GameDay cannot suspend, resume or inject input into the guest; those are the guest's and the
  platform's responsibilities.
- Guest updates depend on the Amazon Appstore. GameDay detects versions and signing changes but
  cannot supply a version feed.
- Guest crashes are not observable without usage access; GameDay reports coarse foreground changes.
- The "app" is only as integrated as Android allows a non-privileged launcher to be: no overlay
  controls during playback, no deep-link insertion into the guest.

## Revisit criteria

Revisit this decision only if **all** of the following become true:

1. A runtime engine exists that is licensed for open-source redistribution.
2. It demonstrably preserves Widevine/secure-surface playback for a third-party guest (evidence:
   measured on an `AFTMM` or successor device).
3. Its added memory and CPU overhead is measured and fits the budget in
   [../PERFORMANCE.md](../PERFORMANCE.md).
4. It does not require defeating integrity checks the guest performs.

Until then, the native host stays.
