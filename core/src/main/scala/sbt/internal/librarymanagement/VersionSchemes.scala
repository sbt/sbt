/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package librarymanagement

import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import sbt.librarymanagement.{ EvictionWarningOptions, ModuleID, ScalaModuleInfo }

// See APIMappings.scala
private[sbt] object VersionSchemes {
  final val EarlySemVer = "early-semver"
  final val SemVerSpec = "semver-spec"
  final val PackVer = "pvp"
  final val Strict = "strict"
  final val Always = "always"

  def validateScheme(value: String): Unit =
    value match {
      case EarlySemVer | SemVerSpec | PackVer | Strict | Always => ()
      case "semver" =>
        sys.error(
          s"""'semver' is ambiguous.
             |Based on the Semantic Versioning 2.0.0, 0.y.z updates are all initial development and thus
             |0.6.0 and 0.6.1 would NOT maintain any compatibility, but in Scala ecosystem it is
             |common to start adopting binary compatibility even in 0.y.z releases.
             |
             |Specify 'early-semver' for the early variant.
             |Specify 'semver-spec' for the spec-correct SemVer.""".stripMargin
        )
      case x => sys.error(s"unknown version scheme: $x")
    }

  /**
   * info.versionScheme property will be included into POM after sbt 1.4.0.
   */
  def extractFromId(mid: ModuleID): Option[String] = extractFromExtraAttributes(mid.extraAttributes)

  def extractFromExtraAttributes(extraAttributes: Map[String, String]): Option[String] =
    extraAttributes.get(SbtPomExtraProperties.VERSION_SCHEME_KEY)

  def evalFunc(
      scheme: String
  ): Function1[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] =
    scheme match {
      case EarlySemVer => EvictionWarningOptions.guessEarlySemVer
      case SemVerSpec  => EvictionWarningOptions.guessSemVer
      case PackVer     => EvictionWarningOptions.evalPvp
      case Strict      => EvictionWarningOptions.guessStrict
      case Always      => EvictionWarningOptions.guessTrue
    }
}
