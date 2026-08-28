/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package server

import java.io.{ File, FileNotFoundException }
import java.nio.file.Files

import scala.util.Success

import verify.BasicTestSuite

// check() tells these outcomes apart, so each one has to stay distinguishable
object ServerIdSpec extends BasicTestSuite:
  private def withPortfile(content: Option[String])(f: File => Unit): Unit =
    val dir = Files.createTempDirectory("portfile").toFile
    val portfile = new File(dir, "active.json")
    content.foreach(sbt.io.IO.write(portfile, _))
    try f(portfile)
    finally sbt.io.IO.delete(dir)

  test("a portfile that is not there"):
    withPortfile(None): portfile =>
      val failure = Server.serverIdOf(portfile).failed.get
      assert(failure.isInstanceOf[FileNotFoundException])

  test("a portfile that is not json"):
    withPortfile(Some("this is not json")): portfile =>
      assert(Server.serverIdOf(portfile).isFailure)

  test("a portfile that names no server"):
    withPortfile(Some("""{"uri":"local:///sock"}""")): portfile =>
      assert(Server.serverIdOf(portfile) == Success(None))

  test("a portfile that names a server"):
    withPortfile(Some("""{"uri":"local:///sock","serverId":"an-id"}""")): portfile =>
      assert(Server.serverIdOf(portfile) == Success(Some("an-id")))
end ServerIdSpec
