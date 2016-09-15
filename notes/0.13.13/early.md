### Fixes with compatibility implications

- sbt 0.13.13 renames the early command that was added in 0.13.1 to `early(<command>)`. This fixes the regression [#1041][1041]. For backward compatibility `--error`, `--warn`, `--info`, and `--debug` will continue to function during 0.13 series, but it is strongly encouraged to migrate to the single hyphen option: `-error`, `-warn`, `-info`, and `-debug`.   [@eed3si9n][@eed3si9n]

  [1041]: https://github.com/sbt/sbt/issues/1041
  [@eed3si9n]: https://github.com/eed3si9n
