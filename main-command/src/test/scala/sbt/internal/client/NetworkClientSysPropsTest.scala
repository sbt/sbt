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

  test("a changed value is a different server"):
    assert(sysProps("-Dmy.prop=first") != sysProps("-Dmy.prop=second"))

  test("dropping an option is a different server"):
    assert(sysProps("-Dmy.prop=first") != sysProps())

  test("a flag without a value is kept"):
    assert(sysProps("-Dmy.prop") == Seq("-Dmy.prop"))

  test("parseArgs keeps the -D options for the server"):
    val arguments = NetworkClient.parseArgs(Array("-Dsbt.test.sysprops=first", "compile"))
    assert(
      NetworkClient.serverSysProps(arguments.sbtArguments) ==
        Seq("-Dsbt.test.sysprops=first")
    )

  test("what is recorded in the portfile reads back"):
    val recorded = sysProps("-Dmy.prop=first", "-Dsbt.version=2.0.7")
    assert(NetworkClient.splitSysProps(recorded.mkString("\n")) == recorded)

  test("a value containing a space reads back in one piece"):
    val recorded = sysProps("-Dmy.prop=a b")
    assert(recorded == Seq("-Dmy.prop=a b"))
    assert(NetworkClient.splitSysProps(recorded.mkString("\n")) == recorded)

  test("a server started without -D options reads back as empty"):
    assert(NetworkClient.splitSysProps("") == Nil)
