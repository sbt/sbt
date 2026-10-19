/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.graph.backend

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.internal.graph.{ GraphModuleId, ModuleGraph }
import _root_.sbt.internal.librarymanagement.GenUpdateReport
import _root_.sbt.librarymanagement.*

object SbtUpdateReportTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "fromConfigurationReport should handle relocated direct dependencies (#8400)",
      testRelocatedDirect
    ),
    example(
      "fromConfigurationReport should handle normal dependencies without relocation",
      testNormal
    ),
    example(
      "fromConfigurationReport should handle transitive relocated dependencies",
      testTransitiveRelocated
    ),
    property(
      "fromConfigurationReport has the root followed by every module in details as nodes",
      propNodes
    ),
    property(
      "fromConfigurationReport only draws edges between nodes of the graph",
      propEdgesClosed
    ),
    property("fromConfigurationReport draws each edge once", propEdgesDistinct),
    property(
      "fromConfigurationReport draws an edge per caller, from the root when the caller is not a node",
      propEdgesFromCallers
    ),
    property(
      "fromConfigurationReport marks an evicted module with the version chosen in its group",
      propEvictedByVersion
    ),
  )

  def gid(m: ModuleID): GraphModuleId = GraphModuleId(m.organization, m.name, m.revision)

  def genRoot: Gen[ModuleID] =
    for
      name <- Gen.element1("app", "core", "root")
      version <- Gen.element1("0.1.0-SNAPSHOT", "1.0.0")
    yield ModuleID("test", name, version)

  def forAllGraphs(
      f: (ConfigurationReport, ModuleID, ModuleGraph) => Result
  ): Property =
    for
      cr <- GenUpdateReport.genConfigName
        .flatMap(GenUpdateReport.genConfigurationReport)
        .forAll
      root <- genRoot.forAll
    yield f(cr, root, SbtUpdateReport.fromConfigurationReport(cr, root))

  def propNodes: Property =
    forAllGraphs: (cr, root, graph) =>
      graph.nodes
        .map(_.id) ==== (gid(root) +: cr.details.flatMap(_.modules).map(r => gid(r.module)))

  def propEdgesClosed: Property =
    forAllGraphs: (_, _, graph) =>
      val ids = graph.nodes.map(_.id).toSet
      Result.all(
        graph.edges.toList.map: (from, to) =>
          Result.assert(ids(from) && ids(to)).log(s"dangling edge $from -> $to")
      )

  def propEdgesDistinct: Property =
    forAllGraphs: (_, _, graph) =>
      graph.edges.distinct ==== graph.edges

  def propEdgesFromCallers: Property =
    forAllGraphs: (cr, root, graph) =>
      val ids = graph.nodes.map(_.id).toSet
      val expected =
        for
          oar <- cr.details
          mr <- oar.modules
          c <- mr.callers
        yield
          val from = gid(c.caller)
          (if ids(from) then from else gid(root), gid(mr.module))
      graph.edges.toSet ==== expected.toSet

  def propEvictedByVersion: Property =
    forAllGraphs: (cr, _, graph) =>
      Result.all(
        cr.details.toList.flatMap: oar =>
          val chosen = oar.modules.find(!_.evicted).map(_.module.revision)
          oar.modules.toList.map: mr =>
            graph.module(gid(mr.module)).flatMap(_.evictedByVersion) ====
              Option.when(mr.evicted)(chosen).flatten
      )

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

  def testRelocatedDirect: Result =
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

    val rootId = GraphModuleId("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val relocatedId = GraphModuleId("at.yawk.lz4", "lz4-java", "1.8.1")
    Result.all(
      List(
        graph.nodes.size ==== 2,
        Result.assert(graph.edges.contains((rootId, relocatedId))).log(s"edges: ${graph.edges}"),
        Result.assert(graph.dependencyMap(rootId).map(_.id).contains(relocatedId)),
      )
    )
  end testRelocatedDirect

  def testNormal: Result =
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

    val rootId = GraphModuleId("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val depId = GraphModuleId("org.example", "example-lib", "1.0.0")
    Result.all(
      List(
        graph.nodes.size ==== 2,
        Result.assert(graph.edges.contains((rootId, depId))).log(s"edges: ${graph.edges}"),
      )
    )
  end testNormal

  def testTransitiveRelocated: Result =
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

    val rootId = GraphModuleId("test", "test-project_2.12", "0.1.0-SNAPSHOT")
    val depAId = GraphModuleId("org.example", "dep-a", "1.0.0")
    val relocatedBId = GraphModuleId("new.group", "dep-b", "2.0.0")
    Result.all(
      List(
        graph.nodes.size ==== 3,
        Result.assert(graph.edges.contains((rootId, depAId))).log(s"edges: ${graph.edges}"),
        Result.assert(graph.edges.contains((rootId, relocatedBId))).log(s"edges: ${graph.edges}"),
      )
    )
  end testTransitiveRelocated
end SbtUpdateReportTest
