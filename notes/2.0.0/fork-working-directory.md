### Forked run and test start in sbt's working directory

Previously, forked `run` and `test` set the forked JVM's working directory to the
project's `baseDirectory`, while non-forked `run` and `test` executed in the directory
sbt itself was started from. In a multi-project build, toggling `fork` silently changed
the directory that relative paths resolved against.

sbt 2.x makes forked `run`, `test`, and `console` inherit sbt's own working directory by
default, consistent with non-forked execution. A new setting,
`forkWorkingDirectory: Option[File]`, configures the working directory of forked
processes. To restore the sbt 1.x behavior:

```scala
Test / forkWorkingDirectory := Some(baseDirectory.value)
run / forkWorkingDirectory := Some(baseDirectory.value)
```

The BSP `buildTarget/jvmRunEnvironment` and `buildTarget/jvmTestEnvironment` responses
report the same working directory so that IDEs follow the same contract.

This addresses [#1032][i1032].

[i1032]: https://github.com/sbt/sbt/issues/1032
