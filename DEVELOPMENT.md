# Cookbook of stuff to do while developing on coursier

General note: always explicitly set the scala version at the sbt prompt, like
```
> ++2.12.4
> ++2.11.11
> ++2.10.6
```

Some modules of coursier are only built in specific scala versions (sbt plugins in 2.10 and 2.12, cli and web modules in 2.11, …). coursier doesn't use sbt-doge
to handle that for now (but any help to make it work would be welcome).

The sources of coursier rely on some git submodules. Clone the sources of coursier via
```
$ git clone --recursive https://github.com/coursier/coursier.git
```
or run
```
$ git submodule update --init --recursive
```
from the coursier sources to initialize them.

The latter command also needs to be run whenever these submodules are updated.

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
> ++2.12.4
> sbt-plugins/publishLocal
> sbt-coursier/scripted sbt-coursier/simple
> sbt-shading/scripted sbt-shading/shading
```

`++2.12.4` sets the scala version, which automatically builds the plugins for sbt 1.0. For sbt 0.13, do `++2.10.6`.

`sbt-plugins/publishLocal` publishes locally the plugins *and their dependencies*, which scripted seems not to do automatically.

## Run all the scripted tests of sbt-coursier or sbt-shading

```
$ sbt
> ++2.12.4
> sbt-plugins/publishLocal
> sbt-coursier/scripted
> sbt-shading/scripted
```

Use `++2.10.6` for sbt 0.13. See discussion above too.

## Run unit tests (JVM)

```
$ sbt
> ++2.12.4
> testsJVM/testOnly coursier.util.TreeTests
> testsJVM/test
```

`testOnly` runs the tests that match the expression it is passed.
`test` runs all the tests.

To run the tests each time the sources change, prefix the test commands with
`~`, like
```
$ sbt
> ++2.12.4
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
> ++2.12.4
> testsJS/testOnly coursier.util.TreeTests
> testsJS/test
```

Like for the JVM tests, prefix test commands with `~` to watch sources (see above).

## Run integration tests

### Main tests

Run the small web repositories with:
```
$ scripts/launch-test-repo.sh --port 8080 --list-pages
$ scripts/launch-test-repo.sh --port 8081
```

Both of these commands spawn a web server in the background.

Run the main ITs with
```
$ sbt ++2.12.4 testsJVM/it:test
```

### Nexus proxy tests

Start the test Nexus servers with
```
$ scripts/launch-proxies.sh
```

This spawns two docker-based Nexus servers in the background (a Nexus 2 and a Nexus 3).

Then run the proxy ITs with
```
$ sbt ++2.12.4 proxy-tests/it:test
```

### Build with Pants

[Pants](https://github.com/pantsbuild/pants) build tool is also added to an experimental path to build the software

Currently only the CLI command can be built via Pants with Scala 2.12.4.

To iterate on code changes:

```
./pants run cli/src/main/scala-2.12:coursier-cli -- fetch --help
```

To build a distributable binary
```
./pants binary cli/src/main/scala-2.12:coursier-cli

# Artifact will be placed under dist/
java -jar dist/coursier-cli.jar fetch --help
```

# Merging PRs on GitHub

Use either "Create merge commit" or "Squash and merge".

Use "Create merge commit" if the commit list is clean enough (each commit has a clear message, and doesn't break simple compilation and test tasks).

Use "Squash and merge" in the other cases.

# General Versioning Guideline

* Major Version 1.x.x : Increment this field when there is a major change.
* Minor Version x.1.x : Increment this field when there is a minor change that breaks backward compatibility for an method.
* Patch version x.x.1 : Increment this field when a minor format change that just adds information that an application can safely ignore.

# Deprecation Strategy

When deprecating a method/field, we want to know
1. Since which version this field/method is being deprecated
2. Migration path, i.e. what to use instead
3. At which point the deprecation will be removed

Due to scala's builtin deprecation works like
```
class deprecated(message: String = {}, since: String = {})
```
we need to put 2) and 3) into `message`:
```
@deprecated(message = "<migration path>. <version to be removed>", since: "deprecation start version")
```

Typically there needs to be at least 2 minor versions between since-version and to-be-removed-version to help migration.

For example, if since version is 1.1.0, then deprecation can be removed in 1.3.0