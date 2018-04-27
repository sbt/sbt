  [StackOverflow]: http://stackoverflow.com/tags/sbt
  [Setup]: http://www.scala-sbt.org/release/docs/Getting-Started/Setup
  [Issues]: https://github.com/sbt/sbt/issues
  [sbt-contrib]: https://gitter.im/sbt/sbt-contrib
  [327]: https://github.com/sbt/sbt/issues/327
  [documentation]: https://github.com/sbt/website

Contributing
============

(For support, see [SUPPORT](./SUPPORT.md))

There are lots of ways to contribute to sbt ecosystem depending on your interests and skill level.

- Help someone at work or online help their build problem.
- Answer StackOverflow questions.
- Create plugins that extends sbt's feature.
- Maintain and update [documentation].
- Garden the issue tracker.
- Report issues.
- Patch the core (send pull requests to code).
- On-ramp other contributors.

Issues and Pull Requests
------------------------

When you find a bug in sbt we want to hear about it. Your bug reports play an important part in making sbt more reliable and usable.

Effective bug reports are more likely to be fixed. These guidelines explain how to write such reports and pull requests.

Please open a GitHub issue when you are 90% sure it's an actual bug.

If you have an enhancement idea, or a general discussion, bring it up to [sbt-contrib].

### Notes about Documentation

Documentation fixes and contributions are as much welcome as to patching the core. Visit [sbt/website][documentation] to learn about how to contribute.

### Preliminaries

- Make sure your sbt version is up to date.
- Search [StackOverflow] and [Issues] to see whether your bug has already been reported.
- Open one case for each problem.
- Proceed to the next steps for details.

### What to report

The developers need three things from you: **steps**, **problems**, and **expectations**.

The most important thing to remember about bug reporting is to clearly distinguish facts and opinions.

#### Steps

What we need first is **the exact steps to reproduce your problems on our computers**. This is called *reproduction steps*, which is often shortened to "repro steps" or "steps." Describe your method of running sbt. Provide `build.sbt` that caused the problem and the version of sbt or Scala that was used. Provide sample Scala code if it's to do with incremental compilation. If possible, minimize the problem to reduce non-essential factors.

Repro steps are the most important part of a bug report. If we cannot reproduce the problem in one way or the other, the problem can't be fixed. Telling us the error messages is not enough.

#### Problems

Next, describe the problems, or what *you think* is the problem. It might be "obvious" to you that it's a problem, but it could actually be an intentional behavior for some backward compatibility etc. For compilation errors, include the stack trace. The more raw info the better.

#### Expectations

Same as the problems. Describe what *you think* should've happened.

#### Notes

Add any optional notes section to describe your analysis.

### Subject

The subject of the bug report doesn't matter. A more descriptive subject is certainly better, but a good subject really depends on the analysis of the problem, so don't worry too much about it. "StackOverflowError while name hashing is enabled" is good enough.

### Formatting

If possible, please format code or console outputs.

On Github it's:

    ```scala
    name := "foo"
    ```

On StackOverflow, it's:

```
<!-- language: lang-scala -->

    name := "foo"
```

Here's a simple sample case: [#327][327].
Finally, thank you for taking the time to report a problem.

Pull Requests
-------------

See below for the branch to work against.

### Adding notes

Most pull requests should include a "Notes" file which documents the change.  This file should reside in the
directory:

    <sbt root>
      notes/
        <target release>/
           <your-change-name>.md

Notes files should have the following contents:

* Bullet item description under one of the following sections:
  - `### Bug fixes`
  - `### Improvements`
  - `### Fixes with compatibility implications`
* Complete section describing new features.

### Clean history

Make sure you document each commit and squash them appropriately. You can use the following guides as a reference:

* Scala's documentation on [Git Hygiene](https://github.com/scala/scala/tree/v2.12.0-M3#git-hygiene)
* Play's documentation on [Working with Git](https://www.playframework.com/documentation/2.4.4/WorkingWithGit#Squashing-commits)

Build from source
=================

### Branch to work against

sbt uses two branches for development:

- Development branch: `1.x` (this is also called "master")
- Stable branch: `1.$MINOR.x`, where `$MINOR` is current minor version (e.g. `1.1.x` during 1.1.x series)

If you're working on a bug fix, it's a good idea to start with the `1.$MINOR.x` branch, since we can always safely merge from stable to `1.x`, but not other way around.

### Instruction to build all modules from source

1. Install the current stable binary release of sbt (see [Setup]), which will be used to build sbt from source.
2. Get the source code.

   ```
   $ mkdir sbt-modules
   $ cd sbt-modules
   $ for i in sbt io util librarymanagement zinc; do \
     git clone https://github.com/sbt/$i.git && (cd $i; git checkout -b 1.1.x origin/1.1.x)
   done
   $ cd sbt
   $ ./sbt-allsources.sh
   ```

3. To build and publish all components locally,

   ```
   $ ./sbt-allsources.sh
   sbt:sbtRoot> publishLocalAllModule
   ```

### Instruction to build just sbt

If the change you are making is contained in sbt/sbt, you could publishLocal on sbt/sbt:

```
$ sbt
sbt:sbtRoot> publishLocal
```

### Using the locally built sbt

The `publishLocal` above will build and publish version `1.$MINOR.$PATCH-SNAPSHOT` (e.g. 1.1.2-SNAPSHOT) to your local ivy repository.

To use the locally built sbt, set the version in `build.properties` file in your project to `1.$MINOR.$PATCH-SNAPSHOT` then launch `sbt` (this can be the `sbt` launcher installed in your machine).

```
$ cd $YOUR_OWN_PROJECT
$ sbt
> compile
```

### Using Jenkins sbt-snapshots nighties

There is a Jenkins instance for sbt that every night builds and publishes (if successful) a timestamped version
of sbt to http://jenkins.scala-sbt.org/sbt-snapshots and is available for 4-5 weeks. To use it do the following:

1. Set the `sbt.version` in `project/build.properties`

```bash
echo "sbt.version=1.2.0-bin-20180423T192044" > project/build.properties
```

2. Create an sbt repositories file (`./repositories`) that includes that Maven repository:

```properties
[repositories]
  local
  local-preloaded-ivy: file:///${sbt.preloaded-${sbt.global.base-${user.home}/.sbt}/preloaded/}, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]
  local-preloaded: file:///${sbt.preloaded-${sbt.global.base-${user.home}/.sbt}/preloaded/}
  maven-central
  sbt-maven-releases: https://repo.scala-sbt.org/scalasbt/maven-releases/, bootOnly
  sbt-maven-snapshots: https://repo.scala-sbt.org/scalasbt/maven-snapshots/, bootOnly
  typesafe-ivy-releases: https://repo.typesafe.com/typesafe/ivy-releases/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext], bootOnly
  sbt-ivy-snapshots: https://repo.scala-sbt.org/scalasbt/ivy-snapshots/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext], bootOnly
  sbt-snapshots: https://jenkins.scala-sbt.org/sbt-snapshots
```

3. Start sbt with a stable launcher and the custom repositories file:

```bash
$ sbt -sbt-jar ~/.sbt/launchers/1.1.4/sbt-launch.jar -Dsbt.repository.config=repositories
Getting org.scala-sbt sbt 1.2.0-bin-20180423T192044  (this may take some time)...
downloading https://jenkins.scala-sbt.org/sbt-snapshots/org/scala-sbt/sbt/1.2.0-bin-20180423T192044/sbt-1.2.0-bin-20180423T192044.jar ...
	[SUCCESSFUL ] org.scala-sbt#sbt;1.2.0-bin-20180423T192044!sbt.jar (139ms)
...
[info] sbt server started at local:///Users/dnw/.sbt/1.0/server/936e0f52ed9baf6b6d83/sock
> show sbtVersion
[info] 1.2.0-bin-20180423T192044
```

### Using Jenkins maven-snapshots nightlies

As an alternative you can request a build that publishes to https://repo.scala-sbt.org/scalasbt/maven-snapshots
and stays there forever by:

1. Logging into https://jenkins.scala-sbt.org/job/sbt-validator/
2. Clicking "Build with Parameters"
3. Making sure `deploy_to_bintray` is enabled
4. Hitting "Build"

Afterwhich start sbt with a stable launcher: `sbt -sbt-jar ~/.sbt/launchers/1.1.4/sbt-launch.jar`

### Clearing out boot and local cache

When you run a locally built sbt, the JAR artifacts will be now cached under `$HOME/.sbt/boot/scala-2.12.6/org.scala-sbt/sbt/1.$MINOR.$PATCH-SNAPSHOT` directory. To clear this out run: `reboot dev` command from sbt's session of your test application.

One drawback of `-SNAPSHOT` version is that it's slow to resolve as it tries to hit all the resolvers. You can workaround that by using a version name like `1.$MINOR.$PATCH-LOCAL1`. A non-SNAPSHOT artifacts will now be cached under `$HOME/.ivy/cache/` directory, so you need to clear that out using [sbt-dirty-money](https://github.com/sbt/sbt-dirty-money)'s `cleanCache` task.

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
[info] shutting down server
[success] Total time: 19 s, completed 25-Apr-2018 15:04:58
```

Please note that this alternative launcher does _not_ have feature parity with sbt/launcher. (Meta)
contributions welcome! :-D

### Diagnosing build failures

Globally included plugins can interfere building `sbt`; if you are getting errors building sbt, try disabling all globally included plugins and try again.

### Running Tests

sbt has a suite of unit tests and integration tests, also known as scripted tests.

#### Unit / Functional tests

Various functional and unit tests are defined throughout the
project. To run all of them, run `sbt test`. You can run a single test
suite with `sbt testOnly`

#### Integration tests

Scripted integration tests reside in `sbt/src/sbt-test` and are
written using the same testing infrastructure sbt plugin authors can
use to test their own plugins with sbt. You can read more about this
style of tests [here](http://www.scala-sbt.org/1.0/docs/Testing-sbt-plugins).

You can run the integration tests with the `sbt scripted` sbt
command. To run a single test, such as the test in
`sbt/src/sbt-test/project/global-plugin`, simply run:

    sbt "scripted project/global-plugin"

Profiling sbt
-------------

See [PROFILING](./PROFILING.md)

Other notes for maintainers
---------------------------

### Publishing VS Code Extensions

Reference https://code.visualstudio.com/docs/extensions/publish-extension

```
$ sbt
> vscodePlugin/compile
> exit
cd vscode-sbt-scala/client
# update version number in vscode-sbt-scala/client/package.json
$ vsce package
$ vsce publish
```

## Signing the CLA

Contributing to sbt requires you or your employer to sign the
[Lightbend Contributor License Agreement](https://www.lightbend.com/contribute/cla).

To make it easier to respect our license agreements, we have added an sbt task
that takes care of adding the LICENSE headers to new files. Run `headerCreate`
and sbt will put a copyright notice into it.
