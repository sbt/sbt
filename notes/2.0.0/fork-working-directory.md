### Forked run starts in sbt's working directory

Previously, forked `run` set the forked JVM's working directory to the project's
`baseDirectory`, while non-forked `run` executed in the directory sbt itself was
started from. In a multi-project build, toggling `fork` silently changed the
directory that relative paths resolved against.

sbt 2.x makes forked `run` (and forked `console`) inherit sbt's own working
directory by default, consistent with non-forked execution and with `sbtn`
expectations. Forked `test` is unchanged and keeps the project's `baseDirectory`
as its working directory. The working directory of any forked process can be
configured via `forkOptions`:

```scala
Compile / run / forkOptions := Def.uncached(
  (Compile / run / forkOptions).value.withWorkingDirectory(Some(baseDirectory.value))
)
```

The BSP `buildTarget/jvmRunEnvironment` response reports the same working
directory that `run` uses.

This addresses [#1032][i1032] for `run`.

[i1032]: https://github.com/sbt/sbt/issues/1032
