### `update` resolves in parallel under the super shell

Building on #9270 (parallel dependency resolution for non-interactive runs), `update` now also
resolves in parallel under the interactive super shell. Previously sbt let coursier draw its own
per-module progress bars there, and those bars cannot be rendered from more than one module at
once, so resolution was serialized one module at a time. sbt now suppresses coursier's bars and
renders a single aggregate progress line at the task level instead, counting distinct files
(metadata and artifacts) and bytes downloaded, e.g.:

```
downloading 240 files, 31.0 MiB 12s
```

so resolution can run concurrently across modules. Setting `csrLogger := Some(...)` opts out: your
logger is used instead, and coursier's behavior is unchanged.

This is the first step of [#5627][i5627]. Per-module status lines (which would add a `status`
field to `ProgressItem`) remain a possible follow-up.

[i5627]: https://github.com/sbt/sbt/issues/5627
