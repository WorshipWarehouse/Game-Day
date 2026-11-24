# Contributing to GameDay

Thanks for helping. This project has one unusual rule that outweighs all the others:

> **Never report an untested result as working.** "Built successfully" is not "runs on the device".
> "Compiles" is not "plays video". Every claim in the docs must be reproducible by someone else.

## 1. Before you start

- Read [docs/RESEARCH.md](docs/RESEARCH.md) and
  [docs/decisions/0001-runtime-selection.md](docs/decisions/0001-runtime-selection.md). The
  architecture is a consequence of evidence; changing it needs new evidence, not preference.
- Check the target: Fire TV Stick 4K 1st gen, Fire OS 6, API 25, 32-bit, 1.5 GB RAM. A change that
  needs API 26+ or arm64 is not acceptable for the primary device.
- Read [SECURITY.md](SECURITY.md). Features that weaken signature checks, DRM, or content protections
  will be rejected.

## 2. Development loop

```bash
./scripts/setup.sh
./scripts/test.sh          # must stay green
./scripts/build.sh --all
./scripts/deploy.sh --connect <fire-tv-ip>   # if you have hardware
```

## 3. What belongs where

| Change | Module |
| --- | --- |
| A rule that decides something (version, compatibility, backoff, redaction) | `core` + a unit test |
| The runtime contract or lifecycle | `runtime-api` + a unit test |
| Android probing, intents, installers, views | `apps/fire-tv` |
| Anything that must be measured on hardware | `compatibility/test-app` |

Keep Android types out of `core` and `runtime-api`. If you need a platform value, pass it in as a
primitive or a small model.

## 4. Code style

- Java 17. No Kotlin plugin, no AndroidX, no third-party runtime libraries — the APK is 108 KB and
  should stay in that order of magnitude.
- Views are built in code (`Ui.menuRow`, `Ui.valueRow`, `Ui.statusRow`); no XML layouts.
- Every user-visible failure needs a stable code (see `RuntimeError`, `CompatibilityIssue`) and a
  human sentence that says what to do next.
- Comments explain *why*, and only where the reason is not obvious from the code.
- Keep methods small enough that the failure paths are visible.

## 5. Tests

- New logic in `core` or `runtime-api` needs a unit test. Bug fixes need a regression test.
- Tests must not depend on a device, network, clock or locale.
- Do not weaken an assertion to make a test pass: if the assertion is right, fix the code; if the
  code is right, fix the test and say why in the commit message.

## 6. Adding a runtime backend

1. Implement `RuntimeBackend` in a new module or under `apps/…/runtime/`.
2. Report capabilities honestly: `UNSUPPORTED` with a reason beats an optimistic `SUPPORTED`.
3. Prove it: extend `compatibility/test-app` so the backend's claims can be measured by a separate
   application.
4. Add an ADR under `docs/decisions/` with measurements, not intentions.
5. Add it to `RuntimeCatalog` so diagnostics show both what exists and what was rejected.

## 7. Commits and pull requests

- One logical change per commit; imperative subject line ("Add verified split-APK validation").
- Say which checks you ran and their result in the PR description, including anything you could not
  run.
- Update the docs affected by the change — especially `docs/COMPATIBILITY.md` and
  `docs/PERFORMANCE.md`, which are supposed to be accurate rather than aspirational.
- Do not commit signing material, keystores, tokens or captured personal data.

## 8. Review checklist

- [ ] `./scripts/test.sh` passes.
- [ ] `./scripts/build.sh --all` produces both APKs.
- [ ] New logic has tests; fixed bugs have regression tests.
- [ ] No new permission without a paragraph explaining why in the PR.
- [ ] No new dependency without a licence check and a size measurement.
- [ ] Claims in docs match what was actually observed.
- [ ] Nothing in the change touches the NFL app's data, credentials or DRM path.

## 9. Licence

Contributions are accepted under GPL-3.0-or-later. By submitting a change you confirm you have the
right to license it that way.
