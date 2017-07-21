  [StackOverflow]: http://stackoverflow.com/tags/sbt
  [ask]: https://stackoverflow.com/questions/ask?tags=sbt
  [Setup]: http://www.scala-sbt.org/release/docs/Getting-Started/Setup
  [Issues]: https://github.com/sbt/sbt/issues
  [sbt-dev]: https://groups.google.com/d/forum/sbt-dev
  [subscriptions]: https://www.lightbend.com/platform/subscription
  [327]: https://github.com/sbt/sbt/issues/327

Issues and Pull Requests
========================

When you find a bug in sbt we want to hear about it. Your bug reports play an important part in making sbt more reliable and usable.

Effective bug reports are more likely to be fixed. These guidelines explain how to write such reports and pull requests.

Preliminaries
--------------

- Make sure your sbt version is up to date.
- Search [StackOverflow] and [Issues] to see whether your bug has already been reported.
- Open one case for each problem.
- Proceed to the next steps for details.

Where to get help and/or file a bug report
------------------------------------------

sbt project uses GitHub Issues as a publicly visible todo list. Please open a GitHub issue only when asked to do so.

- If you need help with sbt, please [ask] on StackOverflow with the tag "sbt" and the name of the sbt plugin if any.
- If you run into an issue, have an enhancement idea, or a general discussion, bring it up to [sbt-dev] Google Group first.
- If you need a faster response time, consider one of the [Lightbend subscriptions][subscriptions].

What to report
--------------

The developers need three things from you: **steps**, **problems**, and **expectations**.

### Steps

The most important thing to remember about bug reporting is to clearly distinguish facts and opinions. What we need first is **the exact steps to reproduce your problems on our computers**. This is called *reproduction steps*, which is often shortened to "repro steps" or "steps." Describe your method of running sbt. Provide `build.sbt` that caused the problem and the version of sbt or Scala that was used. Provide sample Scala code if it's to do with incremental compilation. If possible, minimize the problem to reduce non-essential factors.

Repro steps are the most important part of a bug report. If we cannot reproduce the problem in one way or the other, the problem can't be fixed. Telling us the error messages is not enough.

### Problems

Next, describe the problems, or what *you think* is the problem. It might be "obvious" to you that it's a problem, but it could actually be an intentional behavior for some backward compatibility etc. For compilation errors, include the stack trace. The more raw info the better.

### Expectations

Same as the problems. Describe what *you think* should've happened.

### Notes

Add an optional notes section to describe your analysis.

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

### Branch to work against

Whether implementing a new feature, fixing a bug, or modifying documentation, please work against the latest development branch (currently, 1.0.x).
See below for instructions on building sbt from source.

### Adding notes

All pull requests are required to include a "Notes" file which documents the change.  This file should reside in the
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

Documentation
-------------

Documentation fixes and contributions are as much welcome as to the source code itself. Visit [the website project](https://github.com/sbt/website) to learn about how to contribute.

Build from source
=================

1. Install the current stable binary release of sbt (see [Setup]), which will be used to build sbt from source.
2. Get the source code.

		$ git clone git://github.com/sbt/sbt.git
		$ cd sbt

3. The default branch is the development branch [1.0.x](https://github.com/sbt/sbt/tree/1.0.x), which contains the latest code for the next major sbt release.  To build a specific release or commit, switch to the associated tag.  The tag for the latest stable release is [v0.13.13](https://github.com/sbt/sbt/tree/v0.13.13):

		$ git checkout v0.13.13

	Note that sbt is always built with the previous stable release.  For example, the [1.0.x](https://github.com/sbt/sbt/tree/1.0.x) branch is built with 0.13.13 and the [v0.13.13](https://github.com/sbt/sbt/tree/v0.13.13) tag is built with 0.13.12.

4. To build the launcher and publish all components locally,

		$ sbt
		> publishLocal

5. To use this locally built version of sbt, copy your stable `~/bin/sbt` script to `~/bin/xsbt` and change it to use the launcher jar at `<sbt>/launch/target/sbt-launch.jar`.

	Directory `target` is removed by `clean` command. Second solution is using the artifact stored in the local ivy repository.

	The launcher is located in:

    $HOME/.ivy2/local/org.scala-sbt/sbt-launch/0.13.9/jars/sbt-launch.jar

	for v0.13.9 tag, or in:

    $HOME/.ivy2/local/org.scala-sbt/sbt-launch/0.13.10-SNAPSHOT/jars/sbt-launch.jar

	for the development branch.

## Modifying sbt

1. When developing sbt itself, run `compile` when checking compilation only.

2. To use your modified version of sbt in a project locally, run `publishLocal`.

3. After each `publishLocal`, clean the `~/.sbt/boot/` directory.  Alternatively, if sbt is running and the launcher hasn't changed, run `reboot full` to have sbt do this for you.

4. If a project has `project/build.properties` defined, either delete the file or change `sbt.version` to `1.0.0-SNAPSHOT`.

## Diagnosing build failures

Globally included plugins can interfere building `sbt`; if you are getting errors building sbt, try disabling all globally included plugins and try again.

Running Tests
=============

sbt has an extensive test suite of Unit tests and Integration tests!

Unit / Functional tests
-----------------------

Various functional and unit tests are defined throughout the
project. To run all of them, run `sbt test`. You can run a single test
suite with `sbt testOnly`

Integration tests
-----------------

Scripted integration tests reside in `sbt/src/sbt-test` and are
written using the same testing infrastructure sbt plugin authors can
use to test their own plugins with sbt. You can read more about this
style of tests [here](http://www.scala-sbt.org/1.0/docs/Testing-sbt-plugins).

You can run the integration tests with the `sbt scripted` sbt
command. To run a single test, such as the test in
`sbt/src/sbt-test/project/global-plugin`, simply run:

    sbt "scripted project/global-plugin"

Please note that these tests run PAINFULLY slow if the version set in
`build.sbt` is set to SNAPSHOT, as every time the scripted test boots
up a test instance of sbt, remote mirrors are scanned for possible
updates. It is recommended that you set the version suffix to
`-devel`, as in `1.0.0-devel`.

Building Documentation
======================

The scala-sbt.org site documentation is a separate project [website](https://github.com/sbt/website). Follow [the steps in the README](https://github.com/sbt/website#scala-sbtorg) to generate the documentation.
