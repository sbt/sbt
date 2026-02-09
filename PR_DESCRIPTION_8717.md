# Fix #8717: Handle `--version` flag before native client for sbt 2.x project directories

## Problem

When running `sbt --version` in an sbt 2.x project directory, the script would delegate to the native client (sbtn) instead of printing version information. This caused the command to fail with connection errors instead of displaying the expected version output.

### Reproduction Steps

1. Create an sbt 2.x project:
   ```bash
   mkdir test-project
   cd test-project
   touch build.sbt
   mkdir project
   echo "sbt.version=2.0.0-RC8" > project/build.properties
   ```

2. Run `sbt --version`:
   ```bash
   sbt --version
   ```

**Before the fix:** The script would try to run sbtn and show:
```
[debug] running native client
# Executing command line:
/Users/xxx/.cache/sbt/boot/sbtn/1.12.1/sbtn
--sbt-script=/Users/xxx/work/sbt/sbt
--version
[info] server was not detected. starting an instance
[error] failed to connect to server
```

**After the fix:** The script correctly prints version information:
```
sbt version in this project: 2.0.0-RC8
sbt runner version: 1.12.0

[info] sbt runner (sbt-the-shell-script) is a runner to run any declared version of sbt.
[info] Actual version of the sbt is declared using project/build.properties for each build.
```

## Solution

The fix handles the `--version` flag **before** the native client check, similar to how `--script-version` was fixed in #8711. This ensures that version information is printed directly from the script without delegating to sbtn.

### Changes Made

1. **`sbt` script** (`/home/daniel/sbt-repo/sbt`):
   - Added early handling of `--version` flag before native client delegation
   - Detects if we're in an sbt project directory
   - Prints project sbt version from `build.properties` if available
   - Prints runner version and info messages
   - Exits early to prevent native client delegation

2. **Integration tests**:
   - Added tests in `RunnerScriptTest.scala` for sbt 1.x and 2.x projects
   - Added tests in `ExtendedRunnerTest.scala` for empty directory and sbt 2.x project scenarios
   - All tests verify that version information is printed correctly

3. **Documentation**:
   - Added manual test instructions in `HOW_TO_TEST_8717.md`

## Code Changes

The key change is in the `sbt` script, where we added:

```bash
# Handle --version before native client so it works on sbt 2.x project dirs (#8717)
if [[ $print_version ]]; then
  detect_working_directory
  if [[ -n "$is_this_dir_sbt" ]] && [[ -n "$build_props_sbt_version" ]]; then
    echo "sbt version in this project: $build_props_sbt_version"
  fi
  echo "sbt runner version: $init_sbt_version"
  echo ""
  echo "[info] sbt runner (sbt-the-shell-script) is a runner to run any declared version of sbt."
  echo "[info] Actual version of the sbt is declared using project/build.properties for each build."
  exit 0
fi
```

This is placed right after the `--script-version` handler and before the native client check.

## Testing

### Manual Testing

1. **Test in sbt 2.x project:**
   ```bash
   cd /tmp
   mkdir test-version && cd test-version
   touch build.sbt
   mkdir project
   echo "sbt.version=2.0.0-RC8" > project/build.properties
   sbt --version
   ```
   Expected: Prints version information without trying to run sbtn

2. **Test in empty directory:**
   ```bash
   cd /tmp
   mkdir test-empty && cd test-empty
   sbt --version
   ```
   Expected: Prints runner version only

### Automated Testing

Run the integration tests:
```bash
cd /home/daniel/sbt-repo
sbt "project launcherPackageIntegrationTest" "test"
```

The following tests verify the fix:
- `sbt --version should work (sbt 1.x project)`
- `sbt --version should work (sbt 2.x project) (#8717)`
- `sbt --version in empty directory`
- `sbt --version in sbt 2.x project directory (#8717)`

## Related Issues

- Fixes #8717
- Similar to #8711 (which fixed `--script-version`)

## Notes

- The info messages are printed to stdout (not stderr) to ensure they're captured by the test framework
- This fix maintains backward compatibility - `--version` still works in sbt 1.x projects and empty directories
- The fix follows the same pattern as the `--script-version` fix in #8711

