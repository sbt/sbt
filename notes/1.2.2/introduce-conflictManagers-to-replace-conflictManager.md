## Introduce conflictManagers settingKey

[@elbakramer]: https://github.com/elbakramer

[4318]: https://github.com/sbt/sbt/pull/4318

### Fixes with compatibility implications

- New `conflictManagers` settingKey is introduced which can configure multiple conflict managers.
  - Original `conflictManager` can only configure a single conflict manager.
  - The `conflictManager` has remained for backward compatibility but users are encouraged to use the `conflictManagers` instead of the `conflictManager`.
  - Setting both settingKeys is not the desired case. But for now, it will use both of `conflictManager` and `conflictManagers`, with `conflictManager` value taking precedence.

### Improvements

- Can configure multiple conflict managers by setting `conflictManagers` settingKey [#4318][4318] by [@elbakramer][@elbakramer]

### Bug fixes

- Not a bug actually, but minor changes related to mixed indentation (tabs/spaces).

### Notes

- The order is important when setting `conflictManagers`. Managers on the later side of the sequence won't be considered when one of the previous managers has matching organization/module patterns and handles already.

  For example, following setting that appends managers to the existing `conflictManagers` actually does nothing since it's behind the default manager which handles every case.

  ```sbt
  conflictManagers += ConflictManager.strict // Seq(ConflictManager.default, ConflictManager.strict)
  ```

  If someone wants a different manager to handle some specific cases while falling back to the existing managers, one would prepend the manager instead of appending it.

  ```sbt
  conflictManagers := ConflictManager.strict +: conflictManagers.value
  ```
