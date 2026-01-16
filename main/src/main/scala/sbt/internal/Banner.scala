/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

private[sbt] object Banner:
  def apply(version: String): Option[String] =
    version match
      case v if v.startsWith("2.0.0") =>
        Some(s"""
                |Here are some highlights of sbt $version:
                |  - Scala 3 in metabuild
                |  - Common settings
                |  - test changed to incremental test
                |  - Cache system
                |See https://www.scala-sbt.org/2.x/docs/en/changes/sbt-2.0-change-summary.html
                |Hide the banner for this release by running `skipBanner`.
                |""".stripMargin.linesIterator.mkString("\n"))
      case _ => None
end Banner
