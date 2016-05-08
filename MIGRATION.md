Migration notes
===============

- Build definition is based on Scala 2.11.8
- Build.scala style builds are gone. Use multi-project `build.sbt`.
- `Project(...)` constructor is restricted down to two parameters. Use `project` instead.
- `sbt.Plugin` is also gone. Use auto plugins.
- The incremental compiler, called Zinc, uses class-based name hashing.
- Zinc drops support for Scala 2.8.x and 2.9.x.

#### Additional import required

Implicit conversions are moved to `sbt.syntax`. Add the following imports to auto plugins
or `project/*.scala`.

    import sbt._, syntax._, Keys._
