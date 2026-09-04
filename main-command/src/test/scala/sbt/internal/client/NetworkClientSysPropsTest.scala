/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.client

import verify.BasicTestSuite

object NetworkClientSysPropsTest extends BasicTestSuite:

  private def sysProps(args: String*): Seq[String] =
    NetworkClient.serverSysProps(args.toSeq)

  private def recorded(args: String*): Seq[String] =
    NetworkClient.digestSysProps(sysProps(args*))

  /** What a client carrying `args` makes of the options a server started with `was` recorded. */
  private def diff(was: Seq[String], args: String*): (Seq[String], Seq[String], Seq[String]) =
    NetworkClient.sysPropsDiff(NetworkClient.digestSysProps(sysProps(was*)), sysProps(args*))

  test("-D options describe the server JVM"):
    assert(
      sysProps("-Dmy.prop=first", "-Dsbt.version=2.0.7") ==
        Seq("-Dmy.prop=first", "-Dsbt.version=2.0.7")
    )

  test("options that are not -D are left out"):
    assert(sysProps("-J-Xmx2G", "--server", "compile") == Nil)

  test("options that belong to the client are left out"):
    assert(
      sysProps("-Dsbt.io.virtual=true", "-Dsbt.script=/usr/bin/sbt", "-Dsbt.color=never") ==
        Nil
    )

  test("the order they are given in doesn't matter"):
    assert(sysProps("-Db=2", "-Da=1") == sysProps("-Da=1", "-Db=2"))

  test("a name given twice is the value the JVM ends up with"):
    assert(sysProps("-Dmy.prop=first", "-Dmy.prop=second") == Seq("-Dmy.prop=second"))

  test("the order of two definitions of a name matters"):
    assert(sysProps("-Dx=1", "-Dx=2") != sysProps("-Dx=2", "-Dx=1"))
    assert(diff(Seq("-Dx=1", "-Dx=2"), "-Dx=2", "-Dx=1") == (Nil, Nil, Seq("x")))

  test("a flag without a value is kept"):
    assert(sysProps("-Dmy.prop") == Seq("-Dmy.prop"))

  test("parseArgs keeps the -D options for the server"):
    val arguments = NetworkClient.parseArgs(Array("-Dsbt.test.sysprops=first", "compile"))
    assert(
      NetworkClient.serverSysProps(arguments.sbtArguments) ==
        Seq("-Dsbt.test.sysprops=first")
    )

  test("the same options are the same server"):
    assert(diff(Seq("-Dmy.prop=first"), "-Dmy.prop=first") == (Nil, Nil, Nil))

  test("a changed value is a different server"):
    assert(diff(Seq("-Dmy.prop=first"), "-Dmy.prop=second") == (Nil, Nil, Seq("my.prop")))

  test("dropping an option is a different server"):
    assert(diff(Seq("-Dmy.prop=first")) == (Seq("my.prop"), Nil, Nil))

  test("adding an option is a different server"):
    assert(diff(Nil, "-Dmy.prop=first") == (Nil, Seq("my.prop"), Nil))

  test("what is recorded doesn't give the value away"):
    val entries = recorded("-Dmy.prop=hunter2")
    assert(entries.size == 1, entries.mkString(" "))
    assert(entries.head.startsWith("my.prop="), entries.mkString(" "))
    assert(!entries.head.contains("hunter2"), entries.mkString(" "))

  test("the same option recorded twice doesn't read as the same digest"):
    // each is salted on its own, so the file doesn't say that two servers were started
    // with the same value
    assert(recorded("-Dmy.prop=first") != recorded("-Dmy.prop=first"))
    assert(diff(Seq("-Dmy.prop=first"), "-Dmy.prop=first") == (Nil, Nil, Nil))

  test("what the server inherits reads back"):
    val entries = recorded("-Dmy.prop=first", "-Dsbt.version=2.0.7")
    assert(NetworkClient.decodeSysProps(NetworkClient.encodeSysProps(entries)) == entries)

  test("a value containing a space reads back in one piece"):
    assert(sysProps("-Dmy.prop=a b") == Seq("-Dmy.prop=a b"))
    assert(diff(Seq("-Dmy.prop=a b"), "-Dmy.prop=a b") == (Nil, Nil, Nil))

  test("a value containing a newline reads back in one piece"):
    val multiline = "-Dmy.prop=first\nsecond"
    val entries = NetworkClient.digestSysProps(Seq(multiline))
    assert(NetworkClient.decodeSysProps(NetworkClient.encodeSysProps(entries)) == entries)
    assert(NetworkClient.sysPropsDiff(entries, Seq(multiline)) == (Nil, Nil, Nil))
    assert(
      NetworkClient.sysPropsDiff(entries, Seq("-Dmy.prop=first\nthird")) ==
        (Nil, Nil, Seq("my.prop"))
    )

  test("a server that inherits no -D options reads back as empty"):
    assert(NetworkClient.decodeSysProps("") == Nil)

  test("an unreadable recording is a server worth replacing"):
    assert(NetworkClient.decodeSysProps("not base64 at all") == Nil)
    assert(
      NetworkClient.sysPropsDiff(Seq("my.prop=garbage"), sysProps("-Dmy.prop=first")) ==
        (Nil, Nil, Seq("my.prop"))
    )
