# Testing Terminal Resize Fix

## Manual Testing Steps

To test the fix for cursor jumps and completion list misalignment on terminal resize:

1. **Build sbt locally:**
   ```bash
   cd /root/sbt
   ./sbt publishLocalBin
   ```

2. **Create a test project:**
   ```bash
   mkdir -p /tmp/sbt-test
   cd /tmp/sbt-test
   echo 'sbt.version=2.0.0-RC8-bin-SNAPSHOT' > project/build.properties
   echo 'name := "test"' > build.sbt
   ```

3. **Start sbt shell:**
   ```bash
   sbt
   ```

4. **Test the fix:**
   - Type a partial command like `comp` and press Tab to show completion list
   - While the completion list is displayed, resize your terminal window
   - Verify that:
     - The completion list remains properly aligned in columns
     - The cursor stays in the command line (doesn't jump)
     - The completion list updates to reflect the new terminal width

## Expected Behavior

**Before the fix:**
- Completion list becomes misaligned after resize
- Cursor jumps to a random position

**After the fix:**
- Completion list items remain properly aligned in columns
- Cursor stays in the command line
- Completion list updates correctly with new terminal width

## Code Changes Summary

The fix adds:
1. `invalidateSizeCache()` method to force immediate size refresh
2. SIGWINCH handler registration in `ConsoleTerminal` constructor
3. Cache invalidation when `setSize()` is called
4. Proper cleanup of signal handler in `close()` method

This ensures that when JLine queries the terminal size after a resize, it gets the fresh value immediately.
