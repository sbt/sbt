
  [@Duhemm]: http://github.com/Duhemm
  [2106]: https://github.com/sbt/sbt/pull/2106
  [2197]: https://github.com/sbt/sbt/pull/2197
  [2336]: https://github.com/sbt/sbt/issues/2336

### Fixes with compatibility implications

### Improvements

- Adds configurable compiler bridge. See below.

### Bug fixes

### Configurable Scala compiler bridge

sbt 0.13.10 adds `scalaCompilerBridgeSource` setting to specify the compiler brigde source. This allows different implementation of the bridge for Scala versions, and also allows future versions of Scala compiler implementation to diverge. The source module will be retrieved using library management configured by `bootIvyConfiguration` task.

[#2106][2106]/[#2197][2197]/[#2336][2336] by [@Duhemm][@Duhemm]
