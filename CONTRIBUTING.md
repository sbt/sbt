  [StackOverflow]: http://stackoverflow.com/tags/sbt
  [ask]: https://stackoverflow.com/questions/ask?tags=sbt
  [Setup]: http://www.scala-sbt.org/release/docs/Getting-Started/Setup
  [Issues]: https://github.com/sbt/sbt/issues
  [sbt-dev]: https://groups.google.com/d/forum/sbt-dev
  [sbt-contrib]: https://gitter.im/sbt/sbt-contrib
  [Lightbend]: https://www.lightbend.com/
  [subscriptions]: https://www.lightbend.com/platform/subscription
  [327]: https://github.com/sbt/sbt/issues/327
  [gitter]: https://gitter.im/sbt/sbt
  [documentation]: https://github.com/sbt/website

Support
=======

[Lightbend] sponsors sbt and encourages contributions from the active community. Enterprises can adopt it for mission critical systems with confidence because Lightbend stands behind sbt with commercial support and services.

For community support please [ask] on StackOverflow with the tag "sbt".

- State the problem or question clearly and provide enough context. Code examples and `build.sbt` are often useful when appropriately edited.
- There's also [Gitter sbt/sbt room][gitter], but Stackoverflow is recommended so others can benefit from the answers.

For professional support, [Lightbend], the maintainer of Scala compiler and sbt, provides:

- [Lightbend Subscriptions][subscriptions], which includes Expert Support
- Training
- Consulting

How to contribute to sbt
========================

There are lots of ways to contribute to sbt ecosystem depending on your interests and skill level.

- Help someone at work or online fix their build problem.
- Answer StackOverflow questions.
- Ask StackOverflow questions.
- Create plugins that extend sbt's features.
- Maintain and update [documentation].
- Garden the issue tracker.
- Report issues.
- Patch the core (send pull requests to code).
- On-ramp other contributors.

Issues and Pull Requests
------------------------

When you find a bug in sbt we want to hear about it. Your bug reports play an important part in making sbt more reliable and usable.

Effective bug reports are more likely to be fixed. These guidelines explain how to write such reports and pull requests.

### Notes about Documentation

Documentation fixes and contributions are as much welcome as to patching the core. Visit [the website project][documentation] to learn about how to contribute.

### Preliminaries

- Make sure your sbt version is up to date.
- Search [StackOverflow] and [Issues] to see whether your bug has already been reported.
- Open one case for each problem.
- Proceed to the next steps for details.

### Where to get help and/or file a bug report

sbt project uses GitHub Issues as a publicly visible todo list. Please open a GitHub issue when you are 90% sure it's an actual bug.

- If you need help with sbt, please [ask] on StackOverflow with the tag "sbt" and the name of the sbt plugin if any.
- If you have an enhancement idea, or a general discussion, bring it up to [sbt-contrib].
- If you need a faster response time, consider one of the [Lightbend subscriptions][subscriptions].

### What to report

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

See below for the branch to work against.

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

### Clearing out boot and local cache

When you run a locally built sbt, the JAR artifacts will be now cached under `$HOME/.sbt/boot/scala-2.12.4/org.scala-sbt/sbt/1.$MINOR.$PATCH-SNAPSHOT` directory. To clear this out run: `reboot dev` command from sbt's session of your test application.

One drawback of `-SNAPSHOT` version is that it's slow to resolve as it tries to hit all the resolvers. You can workaround that by using a version name like `1.$MINOR.$PATCH-LOCAL1`. A non-SNAPSHOT artifacts will now be cached under `$HOME/.ivy/cache/` directory, so you need to clear that out using [sbt-dirty-money](https://github.com/sbt/sbt-dirty-money)'s `cleanCache` task.

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

Other notes for maintainers
---------------------------

### Publishing VS Code Extensions


https://code.visualstudio.com/docs/extensions/publish-extension

```
$ sbt
> vscodePlugin/compile
> exit
cd vscode-sbt-scala/client
# update version number in vscode-sbt-scala/client/package.json
$ vsce package
$ vsce publish
```
