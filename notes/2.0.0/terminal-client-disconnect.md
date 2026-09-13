### Fixes

- Don't strand server threads when a client disconnects with a terminal control query unanswered. `VirtualTerminal.cancelRequests` now wakes waiters on all pending terminal maps (set-echo, raw-mode, attributes, size), not just properties and capabilities. Fixes the server-wide freeze where one dying client wedges prompts and command dispatch for every client. ([#6841][6841], [#6840][6840])
- Register raw-mode requests in the raw-mode map (they were registered in the set-echo map).
- Wake terminal input readers with EOF when a channel's terminal closes, so a prompt blocked on a dead client's input unwinds instead of parking forever.
- Read the failed-load prompt byte from the active terminal's input stream instead of `System.in`, which under non-virtual IO is the process's own stdin and never carries client input.
- Deliver EOF reliably on client close: `ServerSessionImpl.close()` now shuts down socket input before closing, so a client disconnect is noticed by the server even while the client's read thread is parked.

[6841]: https://github.com/sbt/sbt/issues/6841
[6840]: https://github.com/sbt/sbt/issues/6840
