  [@dwijnand]: https://github.com/dwijnand
  [1902]: https://github.com/sbt/sbt/pull/1902

### Fixes with compatibility implications

### Improvements

- Facilitate nicer ways of declaring project settings. See below. [#1902][1902] by [@dwijnand][@dwijnand]

### Bug fixes

### Nicer ways of declaring project settings

Now a `Seq[Setting[_]]` can be passed to `Project.settings` without the needs for "varargs expansion", ie. `: _*`

Instead of:

    lazy val foo = project settings (sharedSettings: _*)

It is now possible to do:

    lazy val foo = project settings sharedSettings

Also, `Seq[Setting[_]]` can be declared at the same level as individual settings in `Project.settings`, for instance:

    lazy val foo = project settings (
      sharedSettings,
      version := "1.0",
      someMoreSettings
    )

[#1902][1902] by [@dwijnand][@dwijnand]
