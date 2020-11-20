/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package graph
package backend

import scala.language.implicitConversions
import scala.language.reflectiveCalls
import sbt.librarymanagement.{ ModuleID, ModuleReport, ConfigurationReport }

object SbtUpdateReport {
  type OrganizationArtifactReport = {
    def modules: Seq[ModuleReport]
  }

  def fromConfigurationReport(report: ConfigurationReport, rootInfo: ModuleID): ModuleGraph = {
    implicit def id(sbtId: ModuleID): GraphModuleId =
      GraphModuleId(sbtId.organization, sbtId.name, sbtId.revision)

    def moduleEdges(orgArt: OrganizationArtifactReport): Seq[(Module, Seq[Edge])] = {
      val chosenVersion = orgArt.modules.find(!_.evicted).map(_.module.revision)
      orgArt.modules.map(moduleEdge(chosenVersion))
    }

    def moduleEdge(chosenVersion: Option[String])(report: ModuleReport): (Module, Seq[Edge]) = {
      val evictedByVersion = if (report.evicted) chosenVersion else None
      val jarFile = report.artifacts
        .find(_._1.`type` == "jar")
        .orElse(report.artifacts.find(_._1.extension == "jar"))
        .map(_._2)
      (
        Module(
          id = report.module,
          license = report.licenses.headOption.map(_._1),
          evictedByVersion = evictedByVersion,
          jarFile = jarFile,
          error = report.problem
        ),
        report.callers.map(caller => Edge(caller.caller, report.module))
      )
    }

    val (nodes, edges) = report.details.flatMap(moduleEdges).unzip
    val root = Module(rootInfo)

    ModuleGraph(root +: nodes, edges.flatten)
  }
}
