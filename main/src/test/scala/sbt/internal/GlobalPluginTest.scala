/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import java.io.File
import java.net.URI
import scala.util.Try
import _root_.sbt.librarymanagement.{ ModuleID, UpdateReport, UpdateStats }

object GlobalPluginTest extends Properties:
  override def tests: List[Test] = List(
    example("inject: an exported jar URI with an authority", uriWithAuthority),
  )

  def uriWithAuthority: Result =
    val report = UpdateReport(File("update"), Vector.empty, UpdateStats(0, 0, 0, false), Map.empty)
    val data = GlobalPluginData(
      ModuleID("org.example", "gp", "0.0"),
      Nil,
      Vector.empty,
      Nil,
      Nil,
      Vector(URI("file://server/share/a.jar"))
    )(report)
    Result.assert(Try(GlobalPlugin.inject(data)).isSuccess)
end GlobalPluginTest
