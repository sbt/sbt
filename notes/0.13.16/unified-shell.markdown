
### Fixes with compatibility implications

-

### Improvements

- Unifies sbt shell towards build.sbt DSL syntax. See below.

### Bug fixes

-

### sbt shell accepts build.sbt DSL

sbt shell now accepts the build.sbt DSL notation for the scoped keys.
Instead of the current `<project-id>/config:intask::key`, the shell adds
`key in (<project-id>, <config-ident>, intask)` where `<config-ident>` is the Scala identifier
notation for the configurations like `Compile` and `Test`. (The old shell syntax will continue to function)

These examples will work both from the shell and in build.sbt.

    cancelable in Global
    scalaVersion.in(ThisBuild)
    test in Test
    scalacOptions in (root, Compile, compile)
    name in (Zero, Compile)
    (run in Runtime)

The inspect command now outputs something that can be copy-pasted:

    > inspect compile
    [info] Task: sbt.inc.Analysis
    [info] Description:
    [info]  Compiles sources.
    [info] Provided by:
    [info]  compile in (root, Compile)
    [info] Defined at:
    [info]  (sbt.Defaults) Defaults.scala:326
    [info] Dependencies:
    [info]  manipulateBytecode in (root, Compile)
    [info]  incCompileSetup in (root, Compile)
    [info] Reverse dependencies:
    [info]  printWarnings in (root, Compile)
    [info]  products in (root, Compile)
    [info]  discoveredSbtPlugins in (root, Compile)
    [info]  discoveredMainClasses in (root, Compile)
    [info] Delegates:
    [info]  compile in (root, Compile)
    [info]  compile in root
    [info]  compile in (ThisBuild, Compile)
    [info]  compile in ThisBuild
    [info]  compile in (Zero, Compile)
    [info]  compile in Global
    [info] Related:
    [info]  compile in (zincFoo, Test)
    [info]  compile in (zincFoo, Compile)
    [info]  compile in (root, Test)
    ....

If for some reason, you have to pass `in Runtime` or other valid expression to an input task or a command, you can disambiguate using parenthesis:

    > run in Runtime Universe
    [info] Running example.Hello Universe
    [success] Total time: 0 s, completed Jun 11, 2017 6:36:07 PM
    > (run) in Runtime Universe
    [info] Running example.Hello in Runtime Universe
    [success] Total time: 0 s, completed Jun 11, 2017 6:36:09 PM

[#3259][3259] by [@eed3si9n][@eed3si9n]

  [3259]: https://github.com/sbt/sbt/pull/3259
  [1000]: https://github.com/sbt/sbt/issues/1000
  [@eed3si9n]: https://github.com/eed3si9n
  [@dwijnand]: http://github.com/dwijnand
