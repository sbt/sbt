  [@eed3si9n]: https://github.com/eed3si9n
  [@jsuereth]: https://github.com/jsuereth
  [@dwijnand]: http://github.com/dwijnand
  [@Duhemm]: http://github.com/Duhemm
  [@gkossakowski]: https://github.com/gkossakowski
  [2573]: https://github.com/sbt/sbt/issues/2537

### Bug fixes

- Provides a workaround flag `incOptions := incOptions.value.withIncludeSynthToNameHashing(true)` for name hashing not including synthetic methods. This will not be enabled by default in sbt 0.13. It can also enabled by passing `sbt.inc.include_synth=true` to JVM. [#2537][2573] by [@eed3si9h][@eed3si9h]
