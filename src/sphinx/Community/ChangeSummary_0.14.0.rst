==============
0.14.0 Changes
==============

Overview
========

Features, fixes, changes with compatibility implications
--------------------------------------------------------

- String enrichment to `ProcessBuilder` is deprecated. Use `proc` interpolator.
- `ProcessBuilder`'s `lines` methods are now implicitly added to `ProcessBuilder`. This is to avoid conflict with `StringOp`'s `lines`.

Improvements
------------

- `proc` interpolator. (gh-913) Details below.

Details of major changes
========================

`proc` interpolator
-------------------

`proc` interpolator was added to create `ProcessBuilder` in the build definition.

::

    val gitHeadCommitSha = settingKey[String]("current git commit SHA")

    gitHeadCommitSha in ThisBuild := proc"git rev-parse HEAD".lines.head

Each expression embedded in `proc` strings is treated as an argument, so this could be used to pass strings with white spaces. One exception is an expression of type `Seq[Any]`, which is expanded into a list of arguments.

::

    val grepFoo = taskKey[Unit]("grep for def foo")

    grepFoo := {
      proc"grep ${"def foo"} ${(sources in Compile).value}".!
    }

This is useful when implementing input tasks.

::

    val git = inputKey[Unit]("git")

    git := {
      import sbt.complete.Parsers._
      val args: Seq[String] = spaceDelimited("<arg>").parsed
      proc"git ${args}".!
    }

Note the embedded expression must appear in the beginning of a `proc` string or directly after a whitespace.

::

    val badGrepFoo = taskKey[Unit]("bad grep for def foo")

    badGrepFoo := {
      proc"grep def${" foo"} ${(sources in Compile).value}".!
    }

This will result in an error when the task is called:

::

    [error] (helloworld/*:badGrepFoo) proc found an expression but whitespace was expected:  foo
