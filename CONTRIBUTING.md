# Development guide

## Compiling / testing via the command-line tool

The [command-line tool](https://github.com/alexarchambault/coursier#command-line-1) of coursier allows to quickly test changes in the `core`, `cache`, and `cli` modules. To get a freshly compiled launcher for it, type
```
$ sbt ++2.11.8 cli/pack
```

`++2.11.8` sets the Scala version to `2.11.8`. This is the latest `2.11` Scala version. The launcher is only compiled for Scala `2.11` for now.

`cli/pack` instructs sbt to create a "pack" directory, via the [sbt-pack](https://github.com/xerial/sbt-pack) plugin. Its result can be found under `cli/target/pack`. It mainly contains a `lib` sub-directory, containing the full launcher classpath, along with a `bin` sub-directory, that contains shell scripts allowing to launch applications contained in `lib`.

As the launcher has only one main class, `cli/target/pack/bin` contains only one launcher, named `coursier`. It can be launched like
```
$ cli/target/pack/bin/coursier --help
```

During development, it can be helpful to have the "pack" directory mirror what's in the sources. This can be achieved with
```
$ sbt ++2.11.8 "~cli/pack"
```
which instructs sbt to watch the sources of coursier, and launch `cli/pack` whenever a change is detected.

Note that `cli/pack` triggers a compilation of the `cli` module, along with the modules it depends on (currently, `coreJVM`, the JVM counterpart of the cross-compiled `core` module, and `cache`). That allows to catch compilation errors along the way while generating the "pack" directory.


## Compiling / testing the sbt plugin(s)

### Via scripted

[scripted](http://www.scala-sbt.org/0.13/docs/Testing-sbt-plugins.html) is a test framework for sbt plugins. `sbt-coursier` and `sbt-shading` are tested via scripted.

Running `sbt-coursier` tests requires to have locally published the `coreJVM` and `cache` modules for Scala `2.10`, that `sbt-coursier` depends on. That can be done via
```
$ sbt ++2.10.6 coreJVM/publishLocal cache/publishLocal
```

To run the scripted test suite for `sbt-coursier`, one can then type
```
$ sbt ++2.10.6 sbt-coursier/scripted
```

To run the test suite of `sbt-shading`, publish first the modules it depends on with
```
$ sbt ++2.10.6 coreJVM/publishLocal cache/publishLocal sbt-coursier/publishLocal
```

Then run the test suite per se with
```
$ sbt ++2.10.6 sbt-shading/scripted
```

Running the whole test suites can be cumbersome. scripted allows to run only particular tests, e.g.
```
$ sbt ++2.10.6 "sbt-coursier/scripted sbt-coursier/simple"
```
runs only the `simple` test. The full list of tests for `sbt-coursier` can be found under `sbt-coursier/src/sbt-test/sbt-coursier`, the ones of `sbt-shading` can be found under `sbt-shading/src/sbt-test/sbt-shading`.

Tests can be run continuously, with
```
$ sbt ++2.10.6 "~sbt-coursier/scripted sbt-coursier/simple"
```
which runs the `simple` test of `sbt-coursier` whenever the sources of `sbt-coursier` change. Note that changes in `core` and `cache` are not taken into account straightaway when running tests. These modules have to be published again if they changed (with `sbt ++2.10.6 coreJVM/publishLocal`, `sbt ++2.10.6 cache/publishLocal`, and / or `sbt ++2.10.6 sbt-coursier/publishLocal`, briefly discussed above, the latter only applying when running `sbt-shading` tests, which depends on `sbt-coursier`).

### Via a test project

Alternatively, one can setup a small test project, and have it use a locally published plugin.

Set the coursier version to a test value in `version.sbt`, like
```
$ cat version.sbt
version in ThisBuild := "1.0.0-test-1"
```

Using a custom version, that can be changed if necessary, allows to circumvent possible stale sbt caches (these seem to keep track of the plugin dependencies, and make the plugin fail if these dependencies change) - if the plugin dependencies change, just bump the suffix of the custom version.

Then continuously publish the plugin and its dependencies, with
```
$ sbt ++2.10.6 "~publishLocal"
```

If the compilation went fine, this should print various messages related to the publishing of the modules of coursier under `~/.ivy2/local`, with the custom version set above (here `1.0.0-test-1`).

These published modules, that include the sbt plugins, can then be used straightaway in a test project. For example, one can be setup with
```
$ mkdir -p test-project/project
$ cd test-project
$ echo "sbt.version=0.13.13" > project/build.properties
$ echo 'addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-test-1")' > project/plugins.sbt
$ echo 'scalaVersion := "2.12.1"' > build.sbt
$ sbt
```
and tweaked a bit more depending on what should be tested.


## coursier build matrix

The CI(s) of coursier (Linux and Mac with [Travis](https://travis-ci.org/alexarchambault/coursier/), Windows with [Appveyor](https://ci.appveyor.com/project/alexarchambault/coursier)) test a variety of things. Testing all of those locally can be quite cumbersome. Each of those is susceptible to make the CI fail - often for good reasons, sometimes for less good ones:

- 3 Scala versions (currently, `2.10.6`, `2.11.8`, and `2.12.1`),
- 2 sbt plugins (`sbt-coursier` and `sbt-shading`), each with its own test suite,
- scala-js compilation and tests,
- Windows CI,
- integration tests with external components (currently, a web server and locally published artifacts),
- binary compatibility checks with [MiMA](https://github.com/typesafehub/migration-manager/wiki/Sbt-plugin),
- launcher and sbt-coursier plugin tested against Java 6.
