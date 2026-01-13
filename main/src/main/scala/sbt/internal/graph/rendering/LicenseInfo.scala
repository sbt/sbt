/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package graph
package rendering

import sbt.internal.graph.*
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }
import sjsonnew.*
import sjsonnew.BasicJsonProtocol.*

object LicenseInfo {
  def render(graph: ModuleGraph): String =
    graph.nodes
      .filter(_.isUsed)
      .groupBy(_.license)
      .toSeq
      .sortBy(_._1)
      .map {
        case (license, modules) =>
          license.getOrElse("No license specified") + "\n" +
            modules.map(m => s"\t ${m.id.idString}").mkString("\n")
      }
      .mkString("\n\n")

  def renderJson(graph: ModuleGraph): String = {
    case class LicenseGroup(license: String, modules: Vector[String])
    
    // Use IsoLList pattern for JSON serialization (consistent with codebase style)
    given JsonFormat[LicenseGroup] = LList.iso[LicenseGroup, String :*: Vector[String] :*: LNil](
      { (g: LicenseGroup) =>
        ("license", g.license) :*: ("modules", g.modules) :*: LNil
      },
      { case (_, license) :*: (_, modules) :*: LNil =>
        LicenseGroup(license, modules)
      }
    )

    val groups = graph.nodes
      .filter(_.isUsed)
      .groupBy(_.license)
      .toSeq
      .sortBy(_._1)
      .map {
        case (license, modules) =>
          LicenseGroup(
            license.getOrElse("No license specified"),
            modules.map(_.id.idString).toVector.sorted
          )
      }

    val js = groups.map(Converter.toJsonUnsafe(_))
    js.map(CompactPrinter).mkString("[", ",", "]")
  }
}

