### Fixes

- Don't hang forever when a forked sbt server never writes its portfile. The thin client now bounds the connect wait to 5 minutes (tunable with the `SBT_CLIENT_CONNECT_TIMEOUT` environment variable, in seconds), then fails with a "did not start within N seconds" message followed by the server's captured stderr. Previously the wait was unbounded, and on Windows it ignored process death entirely. Addresses [#9484](https://github.com/sbt/sbt/issues/9484).
