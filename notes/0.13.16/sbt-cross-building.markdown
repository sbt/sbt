
### Improvements

- Ports sbt-cross-building's `^` and `^^` commands for plugin cross building. See below.

### sbt-cross-building

[@jrudolph][@jrudolph]'s sbt-cross-building is a plugin author's plugin.
It adds cross command `^` and sbtVersion switch command `^^`, similar to `+` and `++`,
but for switching between multiple sbt versions across major versions.
sbt 0.13.16 merges these commands into sbt because the feature it provides is useful as we migrate plugins to sbt 1.0.

To switch the `sbtVersion in pluginCrossBuild` from the shell use:

```
^^ 1.0.0-M5
```

Your plugin will now build with sbt 1.0.0-M5 (and its Scala version 2.12.2).

If you need to make changes specific to a sbt version, you can now include them into `src/main/scala-sbt-0.13`,
and `src/main/scala-sbt-1.0.0-M5`, where the binary sbt version number is used as postfix.

To run a command across multiple sbt versions, set:

```scala
crossSbtVersions := Vector("0.13.15", "1.0.0-M5")
```

Then, run:

```
^ compile
```

  [@jrudolph]: https://github.com/jrudolph
  [@eed3si9n]: https://github.com/eed3si9n
