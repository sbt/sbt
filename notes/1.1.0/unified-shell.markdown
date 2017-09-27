
### Fixes with compatibility implications

-

### Improvements

- Unifies sbt shell and build.sbt syntax. See below.

### Bug fixes

-

### Unified slash syntax for sbt shell and build.sbt

This adds unified slash syntax for both sbt shell and the build.sbt DSL.
Instead of the current `<project-id>/config:intask::key`, this adds
`<project-id>/<config-ident>/intask/key` where `<config-ident>` is the Scala identifier
notation for the configurations like `Compile` and `Test`. (The old shell syntax will continue to function)

These examples work both from the shell and in build.sbt.

    Global / cancelable
    ThisBuild / scalaVersion
    Test / test
    root / Compile / compile / scalacOptions
    ProjectRef(uri("file:/xxx/helloworld/"),"root")/Compile/scalacOptions
    Zero / Zero / name

The inspect command now outputs something that can be copy-pasted:

    > inspect compile
    [info] Task: sbt.inc.Analysis
    [info] Description:
    [info]  Compiles sources.
    [info] Provided by:
    [info]  ProjectRef(uri("file:/xxx/helloworld/"),"root")/Compile/compile
    [info] Defined at:
    [info]  (sbt.Defaults) Defaults.scala:326
    [info] Dependencies:
    [info]  Compile/manipulateBytecode
    [info]  Compile/incCompileSetup
    ....

[#3434][3434] by [@eed3si9n][@eed3si9n]

  [3434]: https://github.com/sbt/sbt/pull/3434
  [@eed3si9n]: https://github.com/eed3si9n
  [@dwijnand]: http://github.com/dwijnand
