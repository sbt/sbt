
### Fixes with compatibility implications

- In sbt 1.2, `ScriptedPlugin` is no longer triggered automatically. This allows easier use of the plugin in a multi-project build. We recommend migration to `SbtPlugin`.  [#3514][3514]/[#3875][3875] by [@eed3si9n][@eed3si9n]
- `scriptedBufferLog` and `scriptedLaunchOpts` settings are changed so they are scoped globally.

### Features

- Adds `SbtPlugin`. See below.

### Bug fixes


### SbtPlugin

`SbtPlugin` is a new plugin that represents sbt plugin projects.

    lazy val fooPlugin = (project in file("plugin"))
      .enablePlugins(SbtPlugin)

This sets `sbtPlugin` setting to `true`, and brings in the new non-triggered `ScriptedPlugin`.

[#3875][3875] by [@eed3si9n][@eed3si9n]

  [@eed3si9n]: https://github.com/eed3si9n
  [3514]: https://github.com/sbt/sbt/issues/3514
  [3875]: https://github.com/sbt/sbt/pull/3875
