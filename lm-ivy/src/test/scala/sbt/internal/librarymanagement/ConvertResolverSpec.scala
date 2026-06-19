/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.resolver.AbstractPatternsBasedResolver
import sbt.internal.librarymanagement.ivy.UpdateOptions
import sbt.internal.util.ConsoleLogger
import sbt.librarymanagement.{ Patterns, URLRepository }
import verify.BasicTestSuite

import scala.jdk.CollectionConverters.*

// Regression coverage for sbt/sbt#535: the organization token in an Ivy SFTP/SSH/URL resolver pattern
// should be rendered literally unless the resolver is explicitly Maven-compatible. ConvertResolver is the
// boundary sbt owns: it maps Patterns.isMavenCompatible onto Apache Ivy's setM2compatible, which is what
// makes Ivy keep [organisation] literal (false) or rewrite it to slash form (true).
object ConvertResolverSpec extends BasicTestSuite {
  private val log = ConsoleLogger()

  // The configuration from issue #535: a custom Ivy pattern using the [organisation] token.
  private val orgPatterns =
    Patterns()
      .withIvyPatterns(
        Vector("https://example.org/repo/[organisation]/[module]/ivys/ivy-[revision].xml")
      )
      .withArtifactPatterns(
        Vector(
          "https://example.org/repo/[organisation]/[module]/[type]s/[artifact]-[revision].[ext]"
        )
      )

  private def convert(patterns: Patterns): AbstractPatternsBasedResolver =
    ConvertResolver(URLRepository("test-repo", patterns), new IvySettings, UpdateOptions(), log)
      .asInstanceOf[AbstractPatternsBasedResolver]

  test("the default Patterns keeps the Ivy resolver non-m2compatible (issue #535)") {
    assert(!orgPatterns.isMavenCompatible)
    assert(!convert(orgPatterns).isM2compatible)
  }

  test("isMavenCompatible = false renders the [organisation] token literally") {
    val resolver = convert(orgPatterns)
    assert(!resolver.isM2compatible)
    // sbt forwards the pattern verbatim; with m2compatible off Ivy does not rewrite the organization.
    assert(resolver.getArtifactPatterns.asScala.exists(_.toString.contains("[organisation]")))
  }

  test(
    "isMavenCompatible = true makes the Ivy resolver m2compatible (organization rewritten to slash form)"
  ) {
    assert(convert(orgPatterns.withIsMavenCompatible(true)).isM2compatible)
  }
}
