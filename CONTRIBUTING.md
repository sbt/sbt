  [sbt-dev]: https://groups.google.com/d/forum/sbt-dev‎
  [StackOverflow]: http://stackoverflow.com/tags/sbt  
  [Setup]: http://www.scala-sbt.org/release/docs/Getting-Started/Setup
  [Issues]: https://github.com/sbt/sbt/issues
  [sbt-dev]: https://groups.google.com/d/forum/sbt-dev‎
  [subscriptions]: http://typesafe.com/how/subscription
  [327]: https://github.com/sbt/sbt/issues/327

Issues and Pull Requests
========================

When you find a bug in sbt we want to hear about it. Your bug reports play an important part in making sbt more reliable.

Effective bug reports are more likely to be fixed. These guidelines explain how to write such reports and pull requests.

Preliminatries
--------------

- Make sure your sbt version is up to date.
- Search [StackOverflow] and [Issues] to see whether your bug has already been reported.
- Open one case for each problem.
- Proceed to the next steps for details.

Where to get help and/or file a bug report
------------------------------------------

sbt project uses GitHub issue as a publically visible todo list. Please open a GitHub issue only when asked to do so.

- If you need help with sbt, please ask on [StackOverflow] with the tag "sbt" and the name of the sbt plugin if any.
- If you run into an issue, have an enhancement idea, or a general discussion, bring it up to [sbt-dev] Google Group first.
- If you need faster response time, consider one of the [Typesafe subscriptions][subscriptions].

What to report
--------------

The developers need three things from you: **steps**, **problems**, and **expectations**.

### Steps

The most important thing to remember about bug reporting is to clearly distinguish facts and opinions. What we need first is **the exact steps to reproduce your problems on our computers**. This is called *reproduction steps*, which is often shortened to "repro steps" or "steps." Describe your method of running sbt. Provide `build.sbt` that caused the problem and the version of sbt or Scala that was used. Provide sample Scala code if it's to do with incremental compilation. If possible, minimize the problem to reduce non-essential factors.

Repro steps are the most important part of a bug report. If we cannot reproduce the problem in one way or the other, the problem can't be fixed. Telling us the error messages is not enough.

### Problems

Next, describe the problems, or what *you think* is the problem. It might be "obvious" to you that it's a problem, but it could actually be an intentional behavior for some backward compatibility etc. For complication errors, include stack trace. More raw info the better.

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

Whether implementing a new feature, fixing a bug, or modifying documentation, please work against the latest development branch (currently, 0.13).
Binary compatible changes will be backported to a previous series (currently, 0.12.x) at the time of the next stable release.
See below for instructions on building sbt from source.

Documentation
-------------

Documentation fixes and contributions are welcome.
They are made via pull requests, as described in the previous section.
See below for details on getting sbt sources and modifying the documentation.

Build from source
=================

1. Install the current stable binary release of sbt (see [Setup]), which will be used to build sbt from source.
2. Get the source code.

		$ git clone git://github.com/sbt/sbt.git
		$ cd sbt

3. The initial branch is the development branch 0.13, which contains the latest code for the next major sbt release.  To build a specific release or commit, switch to the associated tag.  The tag for the latest stable release is v0.13.1:

		$ git checkout v0.13.1

	Note that sbt is always built with the previous stable release.  For example, the 0.13 branch is built with 0.13.1 and the v0.13.1 tag is built with 0.13.0.

4. To build the launcher and publish all components locally,

		$ sbt
		> publishLocal

	To build documentation, run `makeSite` or the individual commands directly:

		> doc
		> sphinx:mappings
		> sxr

5. To use this locally built version of sbt, copy your stable `~/bin/sbt` script to `~/bin/xsbt` and change it to use the launcher jar in `<sbt>/target/`.  For the v0.13.1 tag, the full location is:

		<sbt>/target/sbt-launch-0.13.1.jar

	If using the 0.13 development branch, the launcher is at:

		<sbt>/target/sbt-launch-0.13.2-SNAPSHOT.jar

## Modifying sbt

1. When developing sbt itself, run `compile` when checking compilation only.

2. To use your modified version of sbt in a project locally, run `publishLocal`.

3. After each `publishLocal`, clean the `~/.sbt/boot/` directory.  Alternatively, if sbt is running and the launcher hasn't changed, run `reboot full` to have sbt do this for you.

4. If a project has `project/build.properties` defined, either delete the file or change `sbt.version` to `0.13.2-SNAPSHOT`.

Building Documentation
----------------------

The scala-sbt.org site documentation is built using sphinx and requires some external packages to be manually installed first:

```text
$ pip install pygments
$ pip install sphinx
$ pip install sphinxcontrib-issuetracker
```

To build the full site, run the `make-site` task, which will generate the manual, API, SXR, and other site pages in `target/site/`.
To only work on the site and not API or SXR, run `sphinx:mappings`.
To only build API documentation, run `doc`.  Sphinx is not required for generating API or SXR documentation.
