Migration notes
===============

- Build definition is based on Scala 2.12.1
- Build.scala style builds are gone. Use multi-project `build.sbt`.
- `Project(...)` constructor is restricted down to two parameters. Use `project` instead.
- `sbt.Plugin` is also gone. Use auto plugins.
- The incremental compiler, called Zinc, uses class-based name hashing.
- Zinc drops support for Scala 2.8.x, 2.9.x., 2.11.1 and below.
- Removed the pre-0.13.7 *.sbt file parser (previously available under `-Dsbt.parser.simple=true`)
- Removed old, hyphen-separated key names (use `publishLocal` instead of `publish-local`)
- Removes no-longer-documented old operators `<<=`, `<+=`, and `<++=`.
- Renames early command feature from `--<command>` to `early(<command>)`.
- Log options `-error`, `-warn`, `-info`, `-debug` are added as shorthand for `"early(error)"` etc.
- `sbt.Process` and `sbt.ProcessExtra` are gone. Use `scala.sys.process` instead.
- `incOptions.value.withNameHashing(...)` option is removed.
- `TestResult.Value` is now `TestResult`.
- the scripted plugin is cross-versioned now, so you must use %% when depending on it
- Removed the methods settingsSets from Project (along with add/setSbtFiles)
