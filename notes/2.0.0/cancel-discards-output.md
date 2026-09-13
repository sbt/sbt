### Cancelling a task discards its queued output

Previously, cancelling a task that was producing a lot of output (Ctrl+C from the
thin client) stopped the task, but the server kept draining the already-queued
output to the client for several seconds, so the cancel appeared not to take
effect. The cancelled task's buffered stdout/stderr is now discarded on cancel,
so output stops promptly. Control-plane messages and the next command's output
are unaffected.
