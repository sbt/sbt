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
[info] changed: my.prop
[info] my.prop = second
```

A value can be a credential, so it is neither written down nor printed: the connection
file keeps the name of each option and a salted digest of it, which is all the comparison
needs.

If the running server is busy and doesn't shut down, the invocation stops with an error
instead of going ahead with the options it asked for and getting dropped mid-build. With
`-Dsbt.server.autorestart=false` or `-Dsbt.server.autostart=false` the server is left
alone, and the invocation says which options it won't pick up rather than passing them on
in silence.

A server the client itself started with no options at all is a server running without any,
so an invocation that carries some restarts it as well. A server no client started, the one
an editor keeps or a `sbt --server` in another terminal, is left alone: its options were
never written down, it may well have these already, and the invocation only says they might
not be in effect.

Two invocations that both want the server restarted take their turn rather than the second
one taking down the replacement the first just started. The second reads the connection
file again once the first is done, so a server that already has the options it carries is
left running.

Options that describe the client rather than the server (`sbt.color`, `sbt.banner`, ...)
are left out of the comparison, and so is anything in `.sbtopts`, `.jvmopts` or
`SBT_OPTS`, which the client never sees. Other clients attached to that server are
disconnected, the same as with `sbt shutdown`.

This addresses [#9682][i9682].

[i9682]: https://github.com/sbt/sbt/issues/9682
