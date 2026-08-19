### Fixes

- Don't exit silently when the thin client loses its connection to the server mid-run or hits an exception during connect. The client now logs `sbt server disconnected` when the server drops the connection, and prints the exception with a stack trace when the connect or run phase throws. Previously these paths returned exit code 1 with no output, which made CI failures undebuggable. Addresses [#9484](https://github.com/sbt/sbt/issues/9484).
