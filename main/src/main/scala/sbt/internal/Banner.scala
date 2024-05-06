/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

private[sbt] object Banner {
  def apply(version: String): Option[String] =
    version match {
      case v if v.startsWith("1.10.0") =>
        Some(s"""
                |Here are some highlights of sbt 1.10.0:
                |  - SIP-51 support for Scala 2.13 evolution
                |  - Various Zinc fixes
                |  - ConsistentAnalysisFormat: new Zinc Analysis serialization
                |  - CommandProgress API
                |See https://eed3si9n.com/sbt-1.10.0 for full release notes.
                |Hide the banner for this release by running `skipBanner`.
                |""".stripMargin.linesIterator.mkString("\n"))
      case v if v.startsWith("1.9.0") =>
        Some(s"""
                |Here are some highlights of sbt 1.9.0:
                |  - POM consistency of sbt plugin publishing
                |  - sbt new, a text-based adventure
                |  - Deprecation of IntegrationTest configuration
                |See https://eed3si9n.com/sbt-1.9.0 for full release notes.
                |Hide the banner for this release by running `skipBanner`.
                |""".stripMargin.linesIterator.mkString("\n"))
      case v if v.startsWith("1.7.0") =>
        Some(s"""
                |Here are some highlights of this release:
                |  - `++ <sv> <command1>` updates
                |  - Scala 3 compiler error improvements
                |  - Improved Build Server Protocol (BSP) support
                |See https://eed3si9n.com/sbt-1.7.0 for full release notes.
                |Hide the banner for this release by running `skipBanner`.
                |""".stripMargin.linesIterator.mkString("\n"))
      case v if v.startsWith("1.6.0") =>
        Some(s"""
                |Here are some highlights of this release:
                |  - Improved JDK 17 support
                |  - Improved Build Server Protocol (BSP) support
                |  - Tab completion of global keys
                |See https://eed3si9n.com/sbt-1.6.0 for full release notes.
                |Hide the banner for this release by running `skipBanner`.
                |""".stripMargin.linesIterator.mkString("\n"))
      case v if v.startsWith("1.4.0") =>
        Some(s"""
                |Here are some highlights of this release:
                |  - Build server protocol (BSP) support
                |  - sbtn: a native thin client for sbt
                |  - VirtualFile + RemoteCache: caches build artifacts across different machines
                |  - ThisBuild / versionScheme to take the guessing out of eviction warning
                |See http://eed3si9n.com/sbt-1.4.0 for full release notes.
                |Hide the banner for this release by running `skipBanner`.
                |""".stripMargin.linesIterator.mkString("\n"))
      case "1.3.0" =>
        Some(s"""
                |Welcome to sbt $version.
                |Here are some highlights of this release:
                |  - Coursier: new default library management using https://get-coursier.io
                |  - Super shell: displays actively running tasks
                |  - Turbo mode: makes `test` and `run` faster in interactive sessions. Try it by running `set ThisBuild / turbo := true`.
                |See https://www.lightbend.com/blog/sbt-1.3.0-release for full release notes.
                |Hide the banner for this release by running `skipBanner`.
                |""".stripMargin.linesIterator.filter(_.nonEmpty).mkString("\n"))
      case _ => None
    }
}
