### `reboot` works from the thin client again

Running `reboot` in the sbt shell dropped to the OS shell ("sbt server connection
closed") instead of rebooting. The server's teardown began with a log line that
throws when the terminal in scope is the rebooting client's already-closed
virtual terminal, aborting teardown before the server socket was closed and the
portfile deleted; the relaunched instance then mistook the leaked socket for
another running sbt and never started its server, while the client latched onto
the stale portfile. Server teardown now completes its state cleanup before
logging, and one failing channel shutdown can no longer skip the rest of the
exchange teardown. `reboot` returns to a working prompt.

This addresses [#9095][i9095].

[i9095]: https://github.com/sbt/sbt/issues/9095
