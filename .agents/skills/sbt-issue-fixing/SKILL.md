---
name: sbt-issue-fixing
description: Reproduce, investigate, plan fixes for, and fix sbt GitHub issues. Use when the user asks to reproduce an issue such as "reproduce issue ISSUE_URL", fix an issue, investigate an sbt issue, or come up with a plan for an sbt issue fix.
---

# sbt Issue Fixing

Use this skill to build local issue reproductions under `local-temp/issues`, validate reported sbt behavior, and implement focused fixes when requested.

## Issue Reproduction Conventions

Store issue minimizations under `local-temp/issues`.

### Folder Naming

- Create one folder per issue reproduction.
- Use `<issue-id>-issue-name-kebab-case`, where `<issue-id>` is the GitHub issue number and `issue-name-kebab-case` is a short kebab-case name derived from the issue title.

### Required Folder Shape

Each issue reproduction folder should contain:

- `README.md`
- `reproduce.sh`
- `build.sbt`
- the minimal source files needed for the issue

Do not create or rely on a persistent `project/build.properties` unless strictly necessary for issue reproduction. The reproduction script owns sbt version selection and passes it on the command line; see [SBT Version Selection](#sbt-version-selection).

Do not keep generated state in the reproduction folder. Generated `.bsp/`, `target/`, `project/target/`, `.repro-cache/`, and any launcher-created `project/build.properties` must be cleaned by the script.

### Reproduction Fidelity

- Follow the GitHub issue report closely when it includes a minimization. Preserve the same project names, project paths, settings, dependency edges, command, and the presence or absence of source files.
- Keep reproduction mechanics outside the minimized build whenever possible. Cache isolation, sbt version selection, cleanup, and output matching belong in `reproduce.sh`, not in `build.sbt` or `project/build.properties`.
- Always verify the reproduction by running the script after creating or changing it.
- Do not decide reproduction from sbt's exit code alone. Match a focused, issue-specific output fragment that uniquely identifies the reported failure, without matching an entire stack trace.

### SBT Version Selection

The sbt version is always passed on the command line, never via a persistent `project/build.properties`.

- For bare mode, pass the local snapshot version via `--sbt-version`.
- For `--direct` mode, pass the affected sbt version via `--sbt-version`.

The sbt 2.x launcher writes `project/build.properties` automatically when `--sbt-version` is passed. The script's post-run cleanup must remove that file so the reproduction folder is clean between runs.

### SBT Version Modes

Every reproduction script must support exactly these two modes:

- `./reproduce.sh` (bare) - publishes this checkout's sbt snapshot via `publishLocalBin`, then runs the minimized command directly with the local snapshot version passed via `--sbt-version`.
- `./reproduce.sh --direct` - runs the minimized command using the installed `sbt` from PATH with the affected sbt version from the bug report passed via `--sbt-version`.

For bare mode:

- Use the repository root `./sbt` to publish.
- Use the installed `sbt` from PATH to run the minimized command, so the local snapshot is resolved from the publish step.
- The version is the snapshot version from the repository's `build.sbt` (`val v = "..."`).

For `--direct` mode:

- Run from the issue reproduction folder.
- Use the installed `sbt` command from PATH.
- The version is the affected sbt version from the bug report, typically `2.0.0`.
- The `--sbt-version` value is hardcoded in the script, not read from any file.

### Script Lifecycle

The script is a self-contained procedure that owns its own state. It must follow this order:

1. Pre-run cleanup: remove `.bsp/`, `target/`, `project/target/`, any launcher-created `project/build.properties`, and the `.repro-cache/` directory. Create fresh `.repro-cache/{boot,global,local-cache,coursier-cache}` subdirectories.
2. Set state: pass the right `--sbt-version` for the current mode. The script is the only thing that should ever write `project/build.properties`; when sbt's launcher auto-writes it from `--sbt-version`, the script must clean it up.
3. Run sbt with isolated caches: `SBT_LOCAL_CACHE`, `COURSIER_CACHE`, `CSR_CACHE`, `-Dsbt.global.base`, `-Dsbt.boot.directory`, `-Dsbt.io.virtual=false`, and `-Dsbt.server.autostart=false`.
4. Post-run cleanup via `trap` on `EXIT`: remove `project/build.properties`, `project/target`, `.bsp`, and `target` so the folder is clean between runs.

Do not manually edit `project/build.properties` or other generated reproduction-folder files between runs. The script owns its own state; if generated state remains after a run, fix the script's cleanup.

### Script Requirements

`reproduce.sh` must:

- be executable
- follow the [Script Lifecycle](#script-lifecycle) above
- stream sbt output to the terminal without hiding stdout or stderr
- print exactly one final reproduction verdict: `REPRODUCED: yes` or `REPRODUCED: no`
- return control cleanly even when sbt fails

The cleanup must be part of the script itself. Do not rely on manual cleanup before running the script.

### SBT 2 Cache Isolation

SBT 2 uses an operating-system-level action cache by default, so deleting only `target/` is not enough for clean reproductions.

Each reproduction must isolate its sbt state under a project-local `.repro-cache/` directory and delete that directory before each run.

Use this layout:

- `.repro-cache/local-cache` for `sbt.global.localcache` and `SBT_LOCAL_CACHE`
- `.repro-cache/global` for `sbt.global.base`
- `.repro-cache/boot` for `sbt.boot.directory`
- `.repro-cache/coursier-cache` for `COURSIER_CACHE` and `CSR_CACHE` when running the minimization directly

For direct `sbt` runs, prefer:

```bash
SBT_LOCAL_CACHE="$PWD/.repro-cache/local-cache" \
COURSIER_CACHE="$PWD/.repro-cache/coursier-cache" \
CSR_CACHE="$PWD/.repro-cache/coursier-cache" \
sbt --server \
  -Dsbt.io.virtual=false \
  -Dsbt.server.autostart=false \
  -Dsbt.boot.directory="$PWD/.repro-cache/boot" \
  -Dsbt.global.base="$PWD/.repro-cache/global" \
  -Dsbt.global.localcache="$PWD/.repro-cache/local-cache" \
  update
```

Use `--server` plus `-Dsbt.io.virtual=false` and `-Dsbt.server.autostart=false` for direct runs so the native `sbtn` client does not intercept or rewrite arguments.

Do not use `--sbt-boot` in the direct-mode command; use `-Dsbt.boot.directory=...` instead.

### README Requirements

Each reproduction `README.md` must use separate sections for different reproduction styles:

- `Reproduce With Script` - document both modes (`./reproduce.sh` and `./reproduce.sh --direct`) and explain what each tests
- `Clean Reproduction State` - project-local cache cleanup command
- `Manual Reproduction With Locally Published sbt` - `publishLocalBin` plus direct run with `--sbt-version <snapshot>`
- `Manual Reproduction With <affected version>` - direct end-user run with `--sbt-version <affected>` and no `project/build.properties` edit

The README must include:

- the issue number as a markdown link to the GitHub issue
- the two one-command script paths: bare and `--direct`
- the local-publish path using `./sbt publishLocalBin`
- the direct end-user path from the issue folder using only installed `sbt` and `--sbt-version`
- the project-local cache cleanup command
- the focused description of the expected failure on affected sbt versions

## Fixing Issues

### Fix Implementation Standard

- Reproduce the reported issue before implementing a fix whenever feasible.
- Fixes must be surgical: use the smallest code diff that fixes the issue.
- Avoid opportunistic refactors, formatting churn, broad cleanup, or behavior changes outside the failing path.
- Do not add comments to the sbt codebase in the fix diff unless the code would otherwise be materially unclear.
- Add or update tests for the fixed behavior. Prefer focused tests for small scopes and scripted tests when file changes and task coordination are central to the bug.

### Planning a Fix

When the user asks for a plan:

- State what must be reproduced and what command or script will prove it.
- Identify the likely subsystem and files to inspect.
- Propose the smallest behavior change that could fix the issue.
- Specify the regression test shape.
- Specify validation commands for both the affected behavior and the fixed behavior.

### Validation Required For Every Code Change

Every change to issue minimization code or sbt implementation code must be validated before it is considered complete.

For the affected baseline, validate that:

- The reproduction emits the expected erroneous behavior.
- Any regression test fails for the same underlying reason, when such a baseline test run is practical.

For the fixed code, validate that:

- The reproduction no longer emits the erroneous behavior.
- The regression test passes.
- The relevant compile or test command succeeds.

If validation uses an equivalent local checkout or patched workspace instead of a direct baseline-vs-fixed comparison, state that explicitly in the validation report.
