/*
 * Copyright 2015 Johannes Rudolph
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.virtualvoid.sbt.graph
package backend

import scala.language.implicitConversions
import scala.language.reflectiveCalls

import sbt._

object SbtUpdateReport {
  type OrganizationArtifactReport = {
    def modules: Seq[ModuleReport]
  }

  def fromConfigurationReport(report: ConfigurationReport, rootInfo: sbt.ModuleID): ModuleGraph = {
    implicit def id(sbtId: sbt.ModuleID): ModuleId = ModuleId(sbtId.organization, sbtId.name, sbtId.revision)

    def moduleEdges(orgArt: OrganizationArtifactReport): Seq[(Module, Seq[Edge])] = {
      val chosenVersion = orgArt.modules.find(!_.evicted).map(_.module.revision)
      orgArt.modules.map(moduleEdge(chosenVersion))
    }

    def moduleEdge(chosenVersion: Option[String])(report: ModuleReport): (Module, Seq[Edge]) = {
      val evictedByVersion = if (report.evicted) chosenVersion else None
      val jarFile = report.artifacts.find(_._1.`type` == "jar").orElse(report.artifacts.find(_._1.extension == "jar")).map(_._2)
      (Module(
        id = report.module,
        license = report.licenses.headOption.map(_._1),
        evictedByVersion = evictedByVersion,
        jarFile = jarFile,
        error = report.problem),
        report.callers.map(caller ⇒ Edge(caller.caller, report.module)))
    }

    val (nodes, edges) = report.details.flatMap(moduleEdges).unzip
    val root = Module(rootInfo)

    ModuleGraph(root +: nodes, edges.flatten)
  }
}
