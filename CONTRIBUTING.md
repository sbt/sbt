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

On GitHub it's:

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
-----------------

See [DEVELOPING](./DEVELOPING.md)

Profiling sbt
-------------

See [PROFILING](./PROFILING.md)

Other notes for maintainers
---------------------------

Please note that these tests run PAINFULLY slow if the version set in
`build.sbt` is set to SNAPSHOT, as every time the scripted test boots
up a test instance of sbt, remote mirrors are scanned for possible
updates. It is recommended that you set the version suffix to
`-devel`, as in `1.0.0-devel`.

Note for maintainers
====================

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

### Building API docs

1. Rebase wip/unidoc branch https://github.com/eed3si9n/sbt/tree/wip/unidoc on top of the target sbt version.
2. Set the version to the target version.
3. Check out the right versions for all modules locally, and run ./sbt-allsources.sh.
4. ghpagesPushSite

### Building Documentation

The scala-sbt.org site documentation is a separate project [website](https://github.com/sbt/website). Follow [the steps in the README](https://github.com/sbt/website#scala-sbtorg) to generate the documentation.
