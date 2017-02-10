
### Fixes with compatibility implications

- sbt 0.13.14 removes the Maven version range when possible. See below.

### Maven version range improvement

Previously, when the dependency resolver (Ivy) encountered a Maven version range such as `[1.3.0,)`
it would go out to the Internet to find the latest version.
This would result to a surprising behavior where the eventual version keeps changing over time
*even when there's a version of the library that satisfies the range condition*.

Starting sbt 0.13.14, some Maven version ranges would be replaced with its lower bound
so that when a satisfactory version is found in the dependency graph it will be used.
You can disable this behavior using the JVM flag `-Dsbt.modversionrange=false`.

[#2954][2954] by [@eed3si9n][@eed3si9n]

  [@eed3si9n]: https://github.com/eed3si9n
  [@dwijnand]: https://github.com/dwijnand
  [@Duhemm]: https://github.com/Duhemm
  [2954]: https://github.com/sbt/sbt/issues/2954
