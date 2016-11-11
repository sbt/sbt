### Bug fixes

- Fixes a regression in sbt 0.13.12 that was misfiring Scala version enforcement when configuration does not extend `Compile`. [#2827][2827]/[#2786][2786] by [@eed3si9n][@eed3si9n]
- Fixes Scala binary version checking misfiring on configurations that do not extend `Compile`. [#2828][2828]/[#1466][1466] by [@eed3si9n][@eed3si9n]

  [1466]: https://github.com/sbt/sbt/issues/1466
  [2786]: https://github.com/sbt/sbt/issues/2786
  [2827]: https://github.com/sbt/sbt/pull/2827
  [2828]: https://github.com/sbt/sbt/pull/2828
  [@eed3si9n]: https://github.com/eed3si9n
  [@dwijnand]: https://github.com/dwijnand
  [@Duhemm]: https://github.com/Duhemm
