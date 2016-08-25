
### Fixes with compatibility implications

- `.value` is removed from input tasks. Calling `.value` method on an input task returns `InputTask[A]`,
  which is completely unintuitive and often results to a bug. In most cases `.evaluated` should be called,
  which returns `A` by evaluating the task.
  Just in case `InputTask[A]` is needed, `toInputTask` method is now provided.

  [@eed3si9n]: https://github.com/eed3si9n
