Developer guide
===============

### Getting started

Create a [fork](https://docs.github.com/en/github/getting-started-with-github/fork-a-repo) of the repository and [clone](https://docs.github.com/en/github/creating-cloning-and-archiving-repositories/cloning-a-repository) it to create a local copy.

### Branch to work against

sbt uses two branches for development:

- Development branch: `develop` (this is also called "master")
- Stable branch: `1.$MINOR.x`, where `$MINOR` is current minor version (e.g. `1.1.x` during 1.1.x series)

The `develop` branch represents the next major version of sbt. Only new features are pushed to the `develop` branch. This is the branch that you will branch off of to make your changes.
The `stable` branch represents the current stable sbt release. Only bug fixes are back-ported to the stable branch.

### Instruction to build just sbt

Sbt has a number of sub-modules. If the change you are making is just contained in sbt/sbt (not one of the sub-modules),
you can use the `publishLocal` command to publish the sbt project to your local machine. This is helpful for testing your changes.

```
$ sbt
sbt:sbtRoot> publishLocal
```

### Instruction to build all modules from source

When working on a change that requires changing one or more sub modules, the source code for these modules can be pulled in by running the following script
(instead of building them from Maven for example and not being able to change the source code for these sub-modules).

1. Install the current stable binary release of sbt (see [Setup]), which will be used to build sbt from source.
2. Get the source code.

   ```
   $ mkdir sbt-modules
   $ cd sbt-modules
   $ for i in sbt io librarymanagement zinc; do \
     git clone https://github.com/sbt/$i.git && (cd $i; git checkout -b develop origin/develop)
   done
   $ cd sbt
   $ ./sbt-allsources.sh
   ```

3. To build and publish all components locally,

   ```
   $ ./sbt-allsources.sh
   sbt:sbtRoot> publishLocalAllModule
   ```

### Using the locally built sbt

The `publishLocal` command above will build and publish version `1.$MINOR.$PATCH-SNAPSHOT` (e.g. 1.1.2-SNAPSHOT) to your local ivy repository.

To use the locally built sbt, set the version in `build.properties` file in your project to `1.$MINOR.$PATCH-SNAPSHOT` then launch `sbt` (this can be the `sbt` launcher installed in your machine).

```
$ cd $YOUR_OWN_PROJECT
$ sbt
> compile
```

### Nightly builds

_Note: The following section may require an update._

The latest development versions are available as nightly builds on sbt-maven-snapshots (<https://repo.scala-sbt.org/scalasbt/maven-snapshots>) repo, which is a redirect proxy whose underlying repository is subject to change it could be Bintray, Linux box, etc.

To use a nightly build:

1. Find out a version from [/org/scala-sbt/sbt/](https://repo.scala-sbt.org/scalasbt/maven-snapshots/org/scala-sbt/sbt/).
2. Put the version, for example `sbt.version=1.5.0-bin-20201121T081131` in `project/build.properties`.

sbt launcher will resolve the specified sbt core artifacts. Because of the aforementioned redirection, this resolution is going to be very slow for the first time you run sbt, and then it should be ok for subsequent runs.

Unless you're debugging the `sbt` script or the launcher JAR, you should be able to use any recent stable version of sbt installation as the launcher following the [Setup][Setup] instructions first.

If you're overriding the repositories via `~/.sbt/repositories`, make sure that there's a following entry:

```
[repositories]
  ...
  sbt-maven-snapshots: https://repo.scala-sbt.org/scalasbt/maven-snapshots/, bootOnly
```

### Clearing out boot and local cache

sbt consists of lots of JAR files. When running sbt locally, these JAR artifacts are cached in the `boot` directory under `$HOME/.sbt/boot/scala-2.12.6/org.scala-sbt/sbt/1.$MINOR.$PATCH-SNAPSHOT` directory.

In order to see a change you've made to sbt's source code, this cache should be cleared. To clear this out run: `reboot dev` command from sbt's session of your test application.

By default sbt uses a snapshot version (this is a scala convention for quick local changes- it tells users that this version could change).
One drawback of `-SNAPSHOT` version is that it's slow to resolve as it tries to hit all the resolvers.
This is important when testing perfomance, so that the slowness of the resolution does not impact sbt.

You can workaround that by using a version name like `1.$MINOR.$PATCH-LOCAL1`.
A non-SNAPSHOT artifacts will now be cached under `$HOME/.ivy/cache/` directory, so you need to clear that out using [sbt-dirty-money](https://github.com/sbt/sbt-dirty-money)'s `cleanCache` task.

### Running sbt "from source" - `sbtOn`

In addition to locally publishing a build of sbt, there is an alternative, experimental launcher within sbt/sbt
to be able to run sbt "from source", that is to compile sbt and run it from its resulting classfiles rather than
from published jar files.

Such a launcher is available within sbt/sbt's build through a custom `sbtOn` command that takes as its first
argument the directory on which you want to run sbt, and the remaining arguments are passed _to_ that sbt
instance. For example:

I have setup a minimal sbt build in the directory `/s/t`, to run sbt on that directory I call:

```bash
> sbtOn /s/t
[info] Packaging /d/sbt/scripted/sbt/target/scala-2.12/scripted-sbt_2.12-1.2.0-SNAPSHOT.jar ...
[info] Done packaging.
[info] Running (fork) sbt.RunFromSourceMain /s/t
Listening for transport dt_socket at address: 5005
[info] Loading settings from idea.sbt,global-plugins.sbt ...
[info] Loading global plugins from /Users/dnw/.dotfiles/.sbt/1.0/plugins
[info] Loading project definition from /s/t/project
[info] Set current project to t (in build file:/s/t/)
[info] sbt server started at local:///Users/dnw/.sbt/1.0/server/ce9baa494c7598e4d59b/sock
> show baseDirectory
[info] /s/t
> exit
[info] shutting down sbt server
[success] Total time: 19 s, completed 25-Apr-2018 15:04:58
```

Please note that this alternative launcher does _not_ have feature parity with sbt/launcher. (Meta)
contributions welcome! :-D

### Updating Scala version

See https://github.com/sbt/sbt/pull/6522 for the list of files to change for Scala version upgrade.

### Diagnosing build failures

Globally included plugins can interfere building `sbt`; if you are getting errors building sbt, try disabling all globally included plugins and try again.

### Running Tests

sbt has a suite of unit tests and integration tests, also known as scripted tests.

#### Unit / Functional tests

Various functional and unit tests are defined throughout the
project. To run all of them, run `sbt test`. You can run a single test
suite with `sbt testOnly`

#### Integration tests

Scripted integration tests reside in `sbt-app/src/sbt-test` and are
written using the same testing infrastructure sbt plugin authors can
use to test their own plugins with sbt. You can read more about this
style of tests [here](https://www.scala-sbt.org/1.0/docs/Testing-sbt-plugins).

You can run the integration tests with the `sbt scripted` sbt
command. To run a single test, such as the test in
`sbt-app/src/sbt-test/project/global-plugin`, simply run:

    sbt "scripted project/global-plugin"

### Random tidbits

#### Import statements

You'd need alternative DSL import since you can't rely on sbt package object.

```scala
// for slash syntax
import sbt.SlashSyntax0._

// for IO
import sbt.io.syntax._
```
