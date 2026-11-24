# Phase 2 — independent runtime research

Second-pass investigation, commissioned as an adversarial review of the Phase 1 conclusion. It adds
candidates that the first pass did not examine, re-checks the ones it rejected, and separates
"can this run the guest" from "would this run the guest *usefully*".

Status: complete. No physical `AFTMM` was attached, so every claim below is either documentary
(cited), or derived from the source/architecture of the candidate. Device-dependent claims are
marked **[device]** and are deliberately not treated as settled.

## 1. Question being answered

Phase 1 concluded that a native launcher is the only viable design and that no emulator exists for
this device. That conclusion is only useful if it survives a second attempt to break it, so this pass
asked three narrower questions:

1. Is there a **rootless Android guest environment** (not just a launcher) that runs on API 25,
   ARMv7, 1.5 GB, without root?
2. Is there a **compatibility layer** that raises the usable Android API level for the guest?
3. Is there an **isolation mechanism** that is not virtualization but still gives the guest its own
   environment?

## 2. New candidates examined

### 2.1 BlackBox / "virtual engine" (and its forks)

The most serious new candidate: an Android application-virtualization framework derived from
VirtualApp, claiming Apache-2.0 in its documentation, supporting Android 5.0–12.0, minSdk 21, with a
multi-process architecture (`Main`, `Server`, virtual-app processes), hooking `ActivityThread` and
proxying system services.

Findings that decide the question:

- **The canonical project has been discontinued by its author.** The `FBlackBox/BlackBox` README now
  states the author decided to *"dissolve the telegram group and delete this project"* (referencing
  issues #121/#122). What remains is a fork ecosystem (`NewBlackBox`, `BlackBoxReborn`,
  `Blacks-BlackBox`) of varying provenance and quality. A runtime whose upstream no longer exists is
  not a foundation for a security-sensitive host.
- **It still runs the guest inside the host process under the host's UID.** Its own documentation
  describes faking `PackageManager`, `ActivityManager` and `ContentProvider` routing rather than
  changing Linux UIDs. That is the same fundamental arrangement as VirtualApp, with the same
  consequences: weaker isolation than the Android sandbox, and a guest that can detect it is
  virtualized.
- **The README's own scope statement is disqualifying:** *"Stability has not been tested extensively
  and is for learning and communication purposes only."* It also recommends `targetSdkVersion` ≤ 28
  for better compatibility, which is an admission that the hooks depend on framework internals that
  newer platform versions changed.
- **Protected playback is the hard wall.** A `MediaDrm` session is created in the host process and
  the secure decoder is selected against the calling app's identity. Widevine L1 provisioning is
  bound to the device's certified trust chain *and* the app's package identity; substituting the
  identity is precisely the kind of integrity violation the project forbids. The guest also detects
  virtualization: the peer-reviewed study "Demystify Application Virtualization in Android and its
  Security Threats" (ACM SIGMETRICS 2019, §6.2) documents that apps can and do detect virtualization
  frameworks. A streaming app that performs such checks will refuse.

Verdict: unusable for GameDay — licensing provenance, dead upstream, no true isolation, and no
credible path to authorized playback.

### 2.2 Managed profile / work profile

The only rootless isolation mechanism that is *not* virtualization: the platform creates a genuine
second Android user with its own UID, storage and app instances. Open-source DPC tooling exists
(Island, Shelter, and AOSP's `TestDPC`), and it is a *supported* Android feature rather than a hack.

What it would give: real isolation, and — because the guest would be a real app in a real user — the
normal DRM and secure-surface path.

What it cannot give:

- **No new Android API level.** The profile runs the same Fire OS 6 build. It cannot run an app that
  needs API 26+, which is the entire reason a "compatibility layer" would exist.
- **No shared session.** The guest must be installed and signed in again inside the profile.
- **Duplicated storage** for the guest, its downloads and its caches, on an 8 GB device.
- **Provisioning requires platform support**: the `android.software.managed_users` feature and the
  `ACTION_PROVISION_MANAGED_PROFILE` flow. Fire TV has no work-profile UI, so both are **[device]**
  questions. Amazon publishes no managed-profile guidance for Fire TV.

GameDay therefore now *measures* this on the device and reports the verdict honestly
(`IsolationAssessment`, surfaced in the diagnostic report under `isolation`) instead of asserting it
either way. That is the Phase 3 deliverable for this candidate: evidence, not a fake backend.

### 2.3 Closed-source "Android in Android" VM apps (VMOS, VPhoneGaGa class)

These do run a full Android system image inside an app, which makes them the closest thing to the
original vision. They are rejected on every axis that matters here:

- **Closed source**, ad-supported, and they ship a **modified Android system image** — the exact
  supply-chain and integrity risk this project exists to avoid.
- Hundreds of megabytes of guest image on an 8 GB device.
- No Widevine L1; the guest cannot present the device's certified DRM identity.
- Their licences prohibit reuse, so nothing can be learned into the codebase, and bundling one would
  make GameDay non-redistributable.

They are useful only as evidence of what the closed ecosystem accepts: a second, modified Android —
not a compatible one.

### 2.4 Anbox, Cuttlefish, ARC

- **Anbox**: archived; Linux-container design (the same model Waydroid later improved). Not an
  Android-hosted runtime.
- **Cuttlefish**: AOSP's virtual device. Requires a Linux host with KVM (or crosvm on ARM64 hosts) and
  is a development target, not deployable as an app. No DRM.
- **ARC (App Runtime for Chrome)**: deprecated; ChromeOS-only.

### 2.5 Re-check: QEMU/Limbo, Waydroid, AVF, VirtualApp/VirtualXposed

Nothing in this pass changes the Phase 1 verdicts, and one detail sharpens: VirtualXposed *is*
GPL-3.0, but it embeds VirtualApp, whose repository carries no licence grant and whose README claims
all rights and forbids unauthorised use. A GPL wrapper around an unlicensed core does not make the
core redistributable.

## 3. Capability comparison (new candidates only)

| Candidate | Rootless | API 25 host | Own UID | New API level | Protected playback | Licence | Upstream |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Native host (shipped) | yes | yes | guest keeps its own | no | yes | GPL-3.0 | this project |
| BlackBox / forks | yes | partial (5–12) | **no** (host UID) | no | no credible path | unclear/Apache claim | **discontinued** |
| Managed profile | yes | yes **[device]** | **yes** (second user) | no | yes (real user) | platform feature | platform |
| VMOS-class VM app | yes | yes | inside guest image | maybe | no | proprietary | closed |
| Anbox / Cuttlefish / ARC | no | n/a | n/a | n/a | no | OSS | archived |
| Waydroid | no (root, Linux) | no | yes | yes | no | GPL-3.0 | active, Linux only |
| AVF | no (system API) | no (13+/ARM64) | yes | yes | yes in principle | Apache-2.0 | active |
| QEMU/Limbo | yes | yes | yes | yes | no | GPL-2.0 | active |

The column that eliminates almost everything is **protected playback**. The column that eliminates
the rest is **rootless on API 25**.

## 4. Why "no runtime" is the correct engineering answer, not a cop-out

The guest is already a first-class Fire TV application: `minSdk 25`, `armeabi-v7a` present, leanback
launcher, and NFL+ lists Fire TV 6+ as supported. There is nothing for a compatibility layer to
*fix*. The remaining work — launching, verifying, updating, diagnosing — is exactly what GameDay
does.

Adding a virtualization engine would therefore make the product **worse** on every axis the mission
prioritises: reliability of authorized playback, hardware acceleration, memory, stability, and
security.

## 5. Measurements taken in this pass

| Measurement | Value | Source |
| --- | --- | --- |
| GameDay APK (1.0.1) | see `dist/` and `docs/PERFORMANCE.md` | `du -h`, packaging script |
| Native libraries in GameDay | 0 | `unzip -l` |
| BlackBox upstream support range | Android 5.0–12.0, minSdk 21 | project README |
| BlackBox canonical status | author announced deletion | project README |
| Managed profile requirements | `android.software.managed_users` + provisioning flow | AOSP device-admin docs |
| AVF requirements | Android 13+, ARM64, `MANAGE_VIRTUAL_MACHINE` | AOSP virtualization docs |
| Waydroid requirements | GNU/Linux, root, binder | Waydroid docs |

Device-dependent measurements (profile availability on `AFTMM`, guest playback, thermals) remain
**[device]** and are collected by `./scripts/qualify.sh`.

## 6. Sources

- `FBlackBox/BlackBox` repository and README (discontinuation notice, support range, scope caveat).
- `fdavids77/BlackBoxReborn`, `BlackCloud`-class forks (Android 14–16 fixes; fork ecosystem).
- "Demystify Application Virtualization in Android and its Security Threats", *Proc. ACM Meas. Anal.
  Comput. Syst.* 3(1), 2019 — app-level virtualization detection.
- AOSP: device administration, managed provisioning, virtualization framework documentation.
- Waydroid and Anbox project documentation; Amazon Fire OS 6 developer documentation.

## 7. Conclusion

Question 1: **no** rootless Android guest environment exists for API 25 / ARMv7 that preserves
authorized playback.
Question 2: **no** compatibility layer can raise the guest's API level without root or a system
signature.
Question 3: **one** isolation mechanism exists (managed profile) and it is now measured on-device
rather than assumed; it adds isolation, not compatibility.

The architecture decision is recorded in
[decisions/0002-isolation-and-runtime-feasibility.md](decisions/0002-isolation-and-runtime-feasibility.md).
