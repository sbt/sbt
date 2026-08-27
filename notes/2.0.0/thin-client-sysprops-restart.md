### The thin client restarts the server when the `-D` options change

`sbt -Dkey=value` only reaches a server that the client starts itself. Once a server was
running, every later invocation attached to it and its `-D` options were dropped without
a word: exit code 0, no warning, and nothing in the output to tell "flag applied" from
"flag thrown away". They can't be handed to a JVM that is already up, so the workaround
was to remember `sbt shutdown` first.

A client that starts a server now records the `-D` options it passed in
`project/target/active.json`. When a later invocation carries different ones it asks the
running server to shut down and starts a fresh one:

```
$ sbt -Dmy.prop=first showProp
[info] my.prop = first
$ sbt -Dmy.prop=second showProp
[info] sbt server is running with different JVM options; restarting it
[info] dropped: -Dmy.prop=first
[info] added: -Dmy.prop=second
[info] my.prop = second
```

Options that describe the client rather than the server (`sbt.color`, `sbt.banner`, ...)
are left out of the comparison, and so is anything in `.sbtopts`, `.jvmopts` or
`SBT_OPTS`, which the client never sees. Other clients attached to that server are
disconnected, the same as with `sbt shutdown`, and `-Dsbt.server.autorestart=false` turns
the restart off.

This addresses [#9682][i9682].

[i9682]: https://github.com/sbt/sbt/issues/9682
