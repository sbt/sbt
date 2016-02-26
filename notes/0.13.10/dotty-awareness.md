
  [Dotty]: https://github.com/lampepfl/dotty
  [@smarter]: https://github.com/smarter

### Fixes with compatibility implications

### Improvements

- sbt is now aware of [Dotty][Dotty], it will assume
  that Dotty is used when `scalaVersion` starts with `0.`, the sbt
  compiler-bridge does not support Dotty but a separate compiler-bridge is being
  developed at https://github.com/smarter/dotty-bridge and an example project
  that uses it is available at https://github.com/smarter/dotty-example-project
  by [@smarter][@smarter].

### Bug fixes
