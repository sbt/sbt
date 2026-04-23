package sbt
package librarymanagement

import scala.collection.mutable
import sbt.internal.librarymanagement.VersionSchemes
import sbt.util.{ Level, ShowLines }
import EvictionWarningOptions.isNameScalaSuffixed

object EvictionError {
  def apply(
      report: UpdateReport,
      module: ModuleDescriptor,
      schemes: Seq[ModuleID],
  ): EvictionError = {
    apply(report, module, schemes, "always", "always", Level.Debug)
  }

  def apply(
      report: UpdateReport,
      module: ModuleDescriptor,
      schemes: Seq[ModuleID],
      assumedVersionScheme: String,
      assumedVersionSchemeJava: String,
      assumedEvictionErrorLevel: Level.Value,
  ): EvictionError = {
    apply(
      report,
      module,
      schemes,
      assumedVersionScheme,
      assumedVersionSchemeJava,
      assumedEvictionErrorLevel,
      EvictionWarningOptions.default.configurations,
    )
  }

  def apply(
      report: UpdateReport,
      module: ModuleDescriptor,
      schemes: Seq[ModuleID],
      assumedVersionScheme: String,
      assumedVersionSchemeJava: String,
      assumedEvictionErrorLevel: Level.Value,
      configurations: Seq[ConfigRef],
  ): EvictionError = {
    val evictions = EvictionWarning
      .buildEvictions(configurations, report)
    processEvictions(
      module,
      evictions,
      schemes,
      assumedVersionScheme,
      assumedVersionSchemeJava,
      assumedEvictionErrorLevel,
    )
  }

  private[sbt] def processEvictions(
      module: ModuleDescriptor,
      reports: Seq[(ConfigRef, OrganizationArtifactReport)],
      schemes: Seq[ModuleID],
      assumedVersionScheme: String,
      assumedVersionSchemeJava: String,
      assumedEvictionErrorLevel: Level.Value,
  ): EvictionError = {
    val directDependencies = module.directDependencies
    val sbvOpt = module.scalaModuleInfo.map(_.scalaBinaryVersion)
    val userDefinedSchemes: Map[(String, String), String] = Map(schemes flatMap { s =>
      val organization = s.organization
      VersionSchemes.validateScheme(s.revision)
      val versionScheme = s.revision
      (s.crossVersion, sbvOpt) match {
        case (b: Binary, Some("2.13")) =>
          List(
            (s.organization, s"${s.name}${b.suffix}_2.13") -> versionScheme,
            (s.organization, s"${s.name}${b.suffix}_3") -> versionScheme
          )
        case (b: Binary, Some(sbv)) if sbv.startsWith("3.0") || sbv == "3" =>
          List(
            (s.organization, s"${s.name}${b.suffix}_$sbv") -> versionScheme,
            (s.organization, s"${s.name}${b.suffix}_2.13") -> versionScheme
          )
        case (b: Binary, Some(sbv)) =>
          List((s.organization, s"${s.name}${b.suffix}_$sbv") -> versionScheme)
        case _ =>
          List((s.organization, s.name) -> versionScheme)
      }
    }*)
    val pairs = reports
      .flatMap { case (config, detail) =>
        val evicteds = detail.modules filter { _.evicted }
        val winner = (detail.modules filterNot { _.evicted }).headOption
        // don't report on a transitive eviction that does not have a winner
        // https://github.com/sbt/sbt/issues/4946
        winner match {
          case Some(winner) =>
            // from libraryDependencyScheme or defined in the pom using the `info.versionScheme` attribute
            val userDefinedSchemeOrFromPom = {
              def fromLibraryDependencySchemes(org: String = "*", mod: String = "*") =
                userDefinedSchemes.get((org, mod))
              def fromWinnerPom = VersionSchemes.extractFromExtraAttributes(
                winner.extraAttributes.toMap ++ winner.module.extraAttributes
              )

              fromLibraryDependencySchemes(detail.organization, detail.name) // by org and name
                .orElse(fromLibraryDependencySchemes(detail.organization)) // for whole org
                .orElse(fromWinnerPom) // from pom
                .orElse(fromLibraryDependencySchemes()) // global
            }
            val assumedScheme =
              if (isNameScalaSuffixed(detail.name)) assumedVersionScheme
              else assumedVersionSchemeJava

            // We want the user to be able to suppress eviction errors for a specific library,
            // which would result in an incompatible eviction based on the assumed version scheme.
            // So, only fall back to the assumed scheme if there is no given scheme by the user or the pom.
            val (scheme, isAssumed) = userDefinedSchemeOrFromPom
              .map(scheme => (scheme, false))
              .getOrElse((assumedScheme, true))

            val hasIncompatibleVersionForScheme = {
              val isCompat = VersionSchemes.evalFunc(scheme)
              evicteds.exists { r =>
                !isCompat((r.module, Some(winner.module), module.scalaModuleInfo))
              }
            }

            if (hasIncompatibleVersionForScheme)
              Some(
                (
                  EvictionErrorPair(
                    detail.name,
                    detail.organization,
                    winner.module,
                    evicteds.map(_.module),
                    callers(winner, evicteds),
                    scheme,
                    configurations = Vector.empty,
                    isAssumed,
                  ),
                  config
                )
              )
            else None
          case None => None
        }
      }
      // Deduplicate eviction pairs by configuration.
      .groupMap(_._1)(_._2)
      .map { case (pair, configs) =>
        pair.copy(configurations = configs.toVector)
      }

    val (assumedIncompatibleEvictions, incompatibleEvictions) = pairs.partition(_.isAssumed)

    new EvictionError(
      incompatibleEvictions.toList,
      assumedIncompatibleEvictions.toList,
    )
  }

  private def callers(
      winner: ModuleReport,
      evicteds: Vector[ModuleReport],
  ): List[(ModuleID, String)] = {
    val seen: mutable.Set[ModuleID] = mutable.Set()
    (evicteds.toList :+ winner).flatMap { r =>
      val rev = r.module.revision
      r.callers.toList flatMap { caller =>
        if (seen(caller.caller)) Nil
        else {
          seen += caller.caller
          List((caller.caller, rev))
        }
      }
    }
  }

  given evictionErrorLines: ShowLines[EvictionError] = ShowLines { (a: EvictionError) =>
    a.toLines
  }
}

private final case class EvictionErrorPair(
    name: String,
    organization: String,
    winner: ModuleID,
    evicted: Vector[ModuleID],
    callers: List[(ModuleID, String)],
    scheme: String,
    configurations: Vector[ConfigRef],
    isAssumed: Boolean
)

final class EvictionError private[sbt] (
    val incompatibleEvictions: Seq[EvictionErrorPair],
    val assumedIncompatibleEvictions: Seq[EvictionErrorPair],
) {
  def run(): Unit =
    if (incompatibleEvictions.nonEmpty) {
      sys.error(toLines.mkString("\n"))
    }

  def toLines: List[String] = toLines(incompatibleEvictions, false)

  def toAssumedLines: List[String] = toLines(assumedIncompatibleEvictions, true)

  def toLines(
      evictions: Seq[EvictionErrorPair],
      assumed: Boolean
  ): List[String] = {
    val out: mutable.ListBuffer[String] = mutable.ListBuffer()
    out += "found version conflict(s) in library dependencies; some are suspected to be binary incompatible:"
    out += ""
    evictions.foreach({ case a =>
      val callers: List[String] = a.callers.map { case (caller, rev) =>
        f"\t    +- ${caller}%-50s (depends on $rev)"
      }
      val que = if (assumed) "?" else ""
      val evictedRevs = a.evicted.map(_.revision)
      val evictedRevsTitle =
        if (evictedRevs.size <= 1) evictedRevs.mkString
        else evictedRevs.mkString("{", ", ", "}")

      val winnerRev =
        s":${a.winner.revision} (${a.scheme}$que) is selected over ${evictedRevsTitle}"
      val configurationTitle =
        if (a.configurations.size <= 1) a.configurations.mkString
        else a.configurations.mkString("{", ", ", "}")
      val title = s"\t* ${a.organization}:${a.name}$winnerRev for $configurationTitle"
      val lines = title :: callers.reverse ::: List("")
      out ++= lines
    })
    out.toList
  }
}
