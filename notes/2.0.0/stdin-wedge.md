### Interactive client input no longer wedges permanently

The thin client's stdin reader thread lived for exactly one byte, and the
server's next per-byte input request could arrive while that thread was
exiting, in which case the request was silently dropped: no thread ever read
the next byte, the server's terminal read blocked forever, and the session
stopped accepting all keyboard input until the client was killed. Under CPU
load this raced frequently at typing and paste speed. The reader now lives
until the server cancels input reading or stdin ends, and an input request
that arrives while the reader is exiting starts a replacement instead of
being dropped.

This addresses [#9507][i9507].

[i9507]: https://github.com/sbt/sbt/issues/9507
