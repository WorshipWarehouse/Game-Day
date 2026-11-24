# ADR 0002 — Runtime feasibility on the first-generation Fire TV Stick 4K

- **Status:** accepted
- **Date:** 2026-11-24
- **Supersedes:** nothing; extends [0001](0001-runtime-selection.md)
- **Evidence:** [../RESEARCH-PHASE2.md](../RESEARCH-PHASE2.md), [../REVIEW-2026-11.md](../REVIEW-2026-11.md)

## Context

ADR 0001 selected native hosting and rejected the virtualization options available at the time. This
review asked the question again, with new candidates and an adversarial reading of the old evidence,
because "we chose a launcher" is only acceptable if a guest environment is genuinely impossible.

The target is fixed: `AFTMM`, Fire OS 6, Android 7.1 / API 25, 32-bit, 1.5 GB RAM, no root, no
bootloader unlock. Protected playback must stay intact, and nothing may defeat integrity checks.

## Decision

**C — the target hardware cannot support a practical isolated Android runtime without unsupported
system modifications.**

The shipped architecture stays: GameDay is a native host, update manager and diagnostics layer around
the Amazon Appstore build of the NFL app. No experimental runtime backend is merged, because none
exists that passes the playback and licensing bars.

## Evidence

1. **No rootless guest environment survives the playback test.** Every rootless candidate runs the
   guest inside the host's process under the host's UID (VirtualApp class, BlackBox and its forks).
   That loses the guest's own DRM identity and secure-surface path, and is detectable by the guest.
2. **The best-maintained rootless engine was discontinued by its author.** `FBlackBox/BlackBox`
   announces its own deletion; only forks of unclear provenance remain, and its README describes
   itself as untested and "for learning and communication purposes only".
3. **Systems that do provide a real guest need what this device cannot give.** Waydroid: GNU/Linux +
   root + kernel binder. AVF: Android 13+, ARM64, `MANAGE_VIRTUAL_MACHINE`. Cuttlefish: Linux + KVM
   or crosvm. QEMU/Limbo: software CPU emulation that cannot hardware-decode DRM video on a 1.7 GHz
   ARMv7 SoC.
4. **The guest does not need a compatibility layer.** The NFL app targets API 25 minimum, ships
   `armeabi-v7a`, and requires a leanback launcher — all of which Fire OS 6 provides.
5. **The one real isolation mechanism adds isolation, not compatibility.** A managed profile is a
   genuine second user (separate UID, real DRM identity) but runs the same OS build, needs
   re-installation and re-sign-in, duplicates storage, and depends on a provisioning flow Fire TV may
   not ship. GameDay measures this at runtime (`IsolationAssessment`, reported in diagnostics) rather
   than claiming either outcome.

## Consequences

**Positive**

- The product keeps hardware decode, Widevine/HDCP and sign-in on the supported path.
- No dependency on an unlicensed or abandoned engine; the shipped APK has no third-party code.
- The decision is falsifiable: if a candidate ever passes the tests in §Revisit, the runtime contract
  already accommodates it.

**Negative / accepted**

- The product is a launcher+orchestrator, not an emulator, and the documentation says so plainly.
- Input injection, guest crash capture and silent installs remain impossible and are reported as
  unsupported capabilities with reasons.
- Isolation (if a user wants it) is limited to what a managed profile provides, if Fire OS supports it.

## Revisit criteria

Reopen only with evidence, not intent:

1. A runtime exists that is licensed for open-source redistribution.
2. It keeps Widevine/secure-surface playback working for a third-party guest, demonstrated on an
   `AFTMM` or a documented successor.
3. Its memory and CPU overhead is measured and fits the budget in [../PERFORMANCE.md](../PERFORMANCE.md).
4. It does not require defeating integrity checks the guest performs.

If instead the project needs a *newer Android guest*, the answer is decision **D** — a newer Fire OS
device (API 34+/ARM64, where AVF is a real option) — not an emulator on this one.

## What was built instead of a speculative backend

- `IsolationAssessment` (+7 unit tests): a pure-JVM evaluation of managed-profile isolation that
  separates "provisionable" from "useful", and refuses to claim an API level it cannot deliver.
- `DeviceProbe.isolationFacts`: on-device measurement of `android.software.managed_users`, the
  provisioning flow and profile ownership.
- A diagnostic section (`isolation`) so every exported report carries that evidence.
- `./scripts/qualify.sh`: one command that collects device facts, installs, launches, profiles and
  writes a consolidated report for another engineer or agent.

No `RuntimeBackend` for a guest environment was written, because shipping one that cannot play
protected content would repeat the failure mode this project was created to avoid.
