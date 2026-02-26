# Fix for sbt/sbt#4979: sbt -debug doesn't display loading log

## Problem (reproduce)

Without the fix, `sbt -debug` did not show debug-level logs during project loading. Debug output was written to the backing log but the console stayed at Info level until after the `debug` command ran (after loading). Workaround was to run `last` in the sbt shell to see the log file.

## Fix

The launcher passes `-debug` in `configuration.arguments`. We now:

1. Parse the first log-level option (e.g. `-debug`, `--debug`) from startup arguments in `StandardMain.initialState`.
2. Pass that level into `initialGlobalLogging` so the initial console appender uses it (e.g. `Level.Debug`) instead of always `Level.Info`.
3. Set `Keys.logLevel` and `BasicKeys.explicitGlobalLogLevels` in the initial state when a level was detected, so the rest of the run stays at that level.

So debug (or any level) is applied before any command runs, including during project loading.

## Manual verification

1. **Without fix (old behavior):** Run `sbt -debug`; during "Loading project definition..." you would not see debug lines on the console; `last` would show them from the backing file.

2. **With fix:** Run `sbt -debug` (or build sbt and run the published launcher with `-debug`). During loading you should see debug-level log lines on the console (e.g. from `Load.defaultLoad`, dependency resolution, etc.).

3. **Other levels:** `sbt -warn` and `sbt -error` also take effect from the first log line; `sbt` (no option) keeps default Info.
