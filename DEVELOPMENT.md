# Cookbook of stuff done while developing coursier

General note: always explicitly set the scala version at the sbt prompt, like
```
> ++2.12.1
> ++2.11.11
> ++2.10.6
```

Some modules of coursier are only built in specific scala versions (sbt plugins in 2.10 and 2.12, cli and web modules in 2.11, …). coursier doesn't use sbt-doge
to handle that for now (but any help to make it work would be welcome).

## Compile and run the CLI

```
$ sbt ++2.11.11 "project cli" pack
…
$ cli/target/pack/bin/coursier --help
…
```

Note: `sbt ++2.11.11 cli/pack` used to work fine, but doesn't anymore, see
https://github.com/coursier/coursier/commit/3636c58d07532ab2dc176f2d2caa2b4d51050f12.

## Automatically re-compile the CLI

Doesn't work anymore :/ `sbt ++2.11.11 ~cli/pack` used to work, but doesn't
anymore for now (see above). `sbt ++2.11.11 "project cli" ~pack` only watches
the sources of the cli module, not those of the modules it depends on (core,
cache, …).

## Run a scripted test of sbt-coursier or sbt-shading

```
$ sbt
> ++2.12.1
> sbt-plugins/publishLocal
> sbt-coursier/scripted sbt-coursier/simple
> sbt-shading/scripted sbt-shading/shading
```

`++2.12.1` sets the scala version, which automatically builds the plugins for sbt 1.0. For sbt 0.13, do `++2.10.6`.

`sbt-plugins/publishLocal` publishes locally the plugins *and their dependencies*, which scripted seems not to do automatically.

## Run all the scripted tests of sbt-coursier or sbt-shading

```
$ sbt
> ++2.12.1
> sbt-plugins/publishLocal
> sbt-coursier/scripted
> sbt-shading/scripted
```

Use `++2.10.6` for sbt 0.13. See discussion above too.

## Run unit tests (JVM)

```
$ sbt
> ++2.12.1
> testsJVM/testOnly coursier.util.TreeTests
> testsJVM/test
```

`testOnly` runs the tests that match the expression it is passed.
`test` runs all the tests.

To run the tests each time the sources change, prefix the test commands with
`~`, like
```
$ sbt
> ++2.12.1
> ~testsJVM/testOnly coursier.util.TreeTests
> ~testsJVM/test
```

## Run unit tests (JS)

The JS tests require node to be installed, and a few dependencies to have been
fetched with
```
$ npm install
```
(run from the root of the coursier sources).

JS tests can then be run like JVM tests, like
```
$ sbt
> ++2.12.1
> testsJS/testOnly coursier.util.TreeTests
> testsJS/test
```

Like for the JVM tests, prefix test commands with `~` to watch sources (see above).

## Run integration tests

…
