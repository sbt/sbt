[@kxbmap]: https://github.com/kxbmap

[#6673]: https://github.com/sbt/sbt/pull/6673

### Improvements

- Make javaHome that forks scripted tests configurable. [#6673][] by [@kxbmap][]
  - Normally scripted tests are forked using the JVM that is running sbt. If set `scripted / javaHome`, forked using it.
  - Or use `java++` command before scripted.

### Fixes with compatibility implications

- Change type of `scriptedRun` task key from `TaskKey[java.lang.reflect.Method]` to `TaskKey[sbt.ScriptedRun]`
  - `sbt.ScriptedRun` is a new interface for hiding substance of scripted invocation.
