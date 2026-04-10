/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package plugins

import sbt.internal.inc.ScalaInstance

/**
 * An AutoPlugin that wires Scala 3's Best Effort compilation into sbt.
 *
 * When `bestEffortEnabled` is true and the project uses Scala 3.5+,
 * `-Ybest-effort` and `-Ywith-best-effort-tasty` are appended to scalacOptions
 * so that the compiler produces .betasty files (to META-INF/best-effort/ inside classes)
 * even when compilation fails. These files are consumed by IDEs such as Metals
 * for improved code intelligence.
 */
object BestEffortPlugin extends AutoPlugin:
  override def requires = JvmPlugin
  override def trigger = allRequirements

  private[sbt] def isScala35Plus(scalaVersion: String): Boolean =
    ScalaInstance.isDotty(scalaVersion) && {
      val versionPart = scalaVersion.stripPrefix("3.")
      val minor = versionPart.takeWhile(_.isDigit)
      minor.nonEmpty && minor.toInt >= 5
    }
end BestEffortPlugin
