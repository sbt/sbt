
  [@dwijnand]: http://github.com/dwijnand
  [2151]: https://github.com/sbt/sbt/pull/2151

### Fixes with compatibility implications

### Improvements

- Adds `Def.settings`, which facilitates mixing settings with seq of settings. See below.

### Bug fixes

### `Def.settings`

Using `Def.settings` it is now possible to nicely define settings as such:

    val modelSettings = Def.settings(
      sharedSettings,
      libraryDependencies += foo
    )

[#2151][2151] by [@dwijnand][@dwijnand].
