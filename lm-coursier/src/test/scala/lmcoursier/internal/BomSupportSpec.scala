/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package lmcoursier.internal

import java.io.File

import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.Resolver.DefaultMavenRepository
import sbt.librarymanagement.syntax.*

/**
 * Verifies that BOM (Bill of Materials) support works: resolving a real BOM
 * yields dependencyManagement entries (via Coursier's Project or POM fallback).
 */
final class BomSupportSpec extends AnyPropSpec with Matchers {

  private lazy val log = ConsoleLogger()
  private val resolvers = Vector(DefaultMavenRepository)
  private val cache = new File(System.getProperty("java.io.tmpdir"), "lm-coursier-bom-test")
  private val ivyProperties = ResolutionParams.defaultIvyProperties(None)
  private val credentials = Seq.empty[lmcoursier.credentials.Credentials]

  property("BOM resolution returns managed dependencies (junit-bom)") {
    val bomModules = Vector("org.junit" % "junit-bom" % "5.10.0")
    val result = BomSupport.bomForceVersions(
      resolvers,
      bomModules,
      cache,
      log,
      scalaVersion = "2.12.17",
      scalaBinaryVersion = "2.12",
      ivyProperties,
      credentials,
    )
    result should not be empty
    val asSet = result.map { case (mod, ver) =>
      (mod.organization.value, mod.name.value, ver)
    }.toSet
    assert(
      asSet.exists { case (org, name, _) => org == "org.junit.jupiter" && name == "junit-jupiter" },
      s"Expected junit-jupiter in BOM managed deps, got: ${asSet.take(10).mkString(", ")}..."
    )
  }
}
