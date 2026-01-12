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
package backend

import scala.language.implicitConversions
import sbt.librarymanagement.{
  ModuleID,
  ModuleReport,
  ConfigurationReport,
  OrganizationArtifactReport
}

object SbtUpdateReport {

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
    val allNodes = root +: nodes
    val flatEdges = edges.flatten
    val existingNodeIds = allNodes.map(_.id).toSet

    // Handle relocated dependencies where the caller node doesn't exist (#8400)
    val fixedEdges = flatEdges.flatMap { case edge @ (from, to) =>
      if (existingNodeIds.contains(from)) Seq(edge)
      else {
        val callersOfMissing = flatEdges.collect {
          case (caller, target) if target == from => caller
        }
        if (callersOfMissing.isEmpty) Seq(Edge(root.id, to))
        else callersOfMissing.map(caller => Edge(caller, to))
      }
    }

    ModuleGraph(allNodes, fixedEdges.distinct)
  }
}
