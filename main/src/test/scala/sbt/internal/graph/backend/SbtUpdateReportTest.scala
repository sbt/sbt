/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.graph.backend

import verify.BasicTestSuite
import sbt.internal.graph.GraphModuleId
import sbt.librarymanagement.*

object SbtUpdateReportTest extends BasicTestSuite:

  def caller(org: String, name: String, version: String): Caller =
    Caller(
      ModuleID(org, name, version),
      Vector.empty,
      Map.empty,
      isForceDependency = false,
      isChangingDependency = false,
      isTransitiveDependency = false,
      isDirectlyForceDependency = false
    )

  def moduleReport(
      org: String,
      name: String,
      version: String,
      callers: Vector[Caller] = Vector.empty
  ): ModuleReport =
    ModuleReport(
      ModuleID(org, name, version),
      artifacts = Vector.empty,
      missingArtifacts = Vector.empty
    ).withCallers(callers)

  // #8400
  test("fromConfigurationReport should handle relocated direct dependencies"):
    val root = ModuleID("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val relocatedReport = moduleReport(
      "at.yawk.lz4",
      "lz4-java",
      "1.8.1",
      callers = Vector(caller("org.lz4", "lz4-java", "1.8.1"))
    )
    val orgArtReport =
      OrganizationArtifactReport("at.yawk.lz4", "lz4-java", Vector(relocatedReport))
    val configReport = ConfigurationReport(
      ConfigRef("compile"),
      modules = Vector(relocatedReport),
      details = Vector(orgArtReport)
    )

    val graph = SbtUpdateReport.fromConfigurationReport(configReport, root)

    assert(graph.nodes.size == 2)
    val rootId = GraphModuleId("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val relocatedId = GraphModuleId("at.yawk.lz4", "lz4-java", "1.8.1")
    assert(graph.edges.contains((rootId, relocatedId)))
    assert(graph.dependencyMap(rootId).map(_.id).contains(relocatedId))

  test("fromConfigurationReport should handle normal dependencies without relocation"):
    val root = ModuleID("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val normalReport = moduleReport(
      "org.example",
      "example-lib",
      "1.0.0",
      callers = Vector(caller("test", "test-project_2.12", "0.1.0-SNAPSHOT"))
    )
    val orgArtReport =
      OrganizationArtifactReport("org.example", "example-lib", Vector(normalReport))
    val configReport = ConfigurationReport(
      ConfigRef("compile"),
      modules = Vector(normalReport),
      details = Vector(orgArtReport)
    )

    val graph = SbtUpdateReport.fromConfigurationReport(configReport, root)

    assert(graph.nodes.size == 2)
    val rootId = GraphModuleId("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val depId = GraphModuleId("org.example", "example-lib", "1.0.0")
    assert(graph.edges.contains((rootId, depId)))

  test("fromConfigurationReport should handle transitive relocated dependencies"):
    val root = ModuleID("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val depA = moduleReport(
      "org.example",
      "dep-a",
      "1.0.0",
      callers = Vector(caller("test", "test-project_2.12", "0.1.0-SNAPSHOT"))
    )
    val relocatedB = moduleReport(
      "new.group",
      "dep-b",
      "2.0.0",
      callers = Vector(caller("old.group", "dep-b", "2.0.0"))
    )
    val orgArtReportA = OrganizationArtifactReport("org.example", "dep-a", Vector(depA))
    val orgArtReportB = OrganizationArtifactReport("new.group", "dep-b", Vector(relocatedB))
    val configReport = ConfigurationReport(
      ConfigRef("compile"),
      modules = Vector(depA, relocatedB),
      details = Vector(orgArtReportA, orgArtReportB)
    )

    val graph = SbtUpdateReport.fromConfigurationReport(configReport, root)

    assert(graph.nodes.size == 3)
    val rootId = GraphModuleId("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val depAId = GraphModuleId("org.example", "dep-a", "1.0.0")
    val relocatedBId = GraphModuleId("new.group", "dep-b", "2.0.0")
    assert(graph.edges.contains((rootId, depAId)))
    assert(graph.edges.contains((rootId, relocatedBId)))
end SbtUpdateReportTest
