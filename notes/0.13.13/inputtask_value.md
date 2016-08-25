
### Fixes with compatibility implications

- `.value` method is deprecated for input tasks. Calling `.value` method on an input task returns `InputTask[A]`,
  which is completely unintuitive and often results to a bug. In most cases `.evaluated` should be called,
  which returns `A` by evaluating the task.
  Just in case `InputTask[A]` is needed, `toInputTask` method is now provided. [#2709][2709] by [@eed3si9n][@eed3si9n]

  [@eed3si9n]: https://github.com/eed3si9n
  [2709]: https://github.com/sbt/sbt/pull/2709
