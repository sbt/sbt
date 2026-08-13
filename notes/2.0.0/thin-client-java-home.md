### The thin client honors `-java-home` when it starts a server

Launcher value flags such as `-java-home` were parsed by the thin client but then
dropped: the `=` form (`--java-home=/path`) was forwarded to the server verbatim
and rejected as a command (`Not a valid command: --`), and the space form
(`-java-home /path`) was silently discarded when the client had to start a server,
so that server came up under the default JVM. Both forms are now consumed by the
client and re-passed to a server it starts, so the server runs under the requested
JVM, including values that contain spaces such as a Windows path
(`C:\Program Files\Java\...`). This also applies to the other launcher value flags
(`-mem`, `-jvm-debug`, `-sbt-dir`, ...), which were dropped the same way.
