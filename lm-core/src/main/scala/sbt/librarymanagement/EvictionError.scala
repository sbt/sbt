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
    val evictions = configurations.flatMap { configuration =>
      EvictionWarning.buildEvictions(Vector(configuration), report)
    }
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
      reports: Seq[OrganizationArtifactReport],
      schemes: Seq[ModuleID],
      assumedVersionScheme: String,
      assumedVersionSchemeJava: String,
      assumedEvictionErrorLevel: Level.Value,
  ): EvictionError = {
    val directDependencies = module.directDependencies
    val pairs = reports
      .map { detail =>
        val evicteds = detail.modules filter { _.evicted }
        val winner = (detail.modules filterNot { _.evicted }).headOption
        new EvictionPair(
          detail.organization,
          detail.name,
          winner,
          evicteds,
          includesDirect = true,
          showCallers = true
        )
      }
    val incompatibleEvictions: mutable.ListBuffer[EvictionErrorPair] = mutable.ListBuffer()
    val assumedIncompatibleEvictions: mutable.ListBuffer[EvictionErrorPair] = mutable.ListBuffer()
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

    pairs foreach {
      // don't report on a transitive eviction that does not have a winner
      // https://github.com/sbt/sbt/issues/4946
      case p if p.winner.isDefined =>
        val winner = p.winner.get

        def hasIncompatibleVersionForScheme(scheme: String) = {
          val isCompat = VersionSchemes.evalFunc(scheme)
          p.evicteds.exists { r =>
            !isCompat((r.module, Some(winner.module), module.scalaModuleInfo))
          }
        }

        // from libraryDependencyScheme or defined in the pom using the `info.versionScheme` attribute
        val userDefinedSchemeOrFromPom = {
          def fromLibraryDependencySchemes(org: String = "*", mod: String = "*") =
            userDefinedSchemes.get((org, mod))
          def fromWinnerPom = VersionSchemes.extractFromExtraAttributes(
            winner.extraAttributes.toMap ++ winner.module.extraAttributes
          )

          fromLibraryDependencySchemes(p.organization, p.name) // by org and name
            .orElse(fromLibraryDependencySchemes(p.organization)) // for whole org
            .orElse(fromWinnerPom) // from pom
            .orElse(fromLibraryDependencySchemes()) // global
        }

        def callers(
            winner: Option[ModuleReport],
            evicteds: Vector[ModuleReport],
        ): List[(ModuleID, String)] = {
          val seen: mutable.Set[ModuleID] = mutable.Set()
          (evicteds.toList ::: winner.toList).flatMap { r =>
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

        // We want the user to be able to suppress eviction errors for a specific library,
        // which would result in an incompatible eviction based on the assumed version scheme.
        // So, only fall back to the assumed scheme if there is no given scheme by the user or the pom.
        userDefinedSchemeOrFromPom match {
          case Some(givenScheme) =>
            if (hasIncompatibleVersionForScheme(givenScheme)) {
              val eviction = EvictionErrorPair(
                p.name,
                p.organization,
                p.winner.map(_.module.revision),
                p.evicteds.map(_.module.revision),
                callers(p.winner, p.evicteds),
                givenScheme
              )
              if (!incompatibleEvictions.contains(eviction)) {
                incompatibleEvictions += eviction
              }
            }
          case None =>
            val assumedScheme =
              if (isNameScalaSuffixed(p.name)) assumedVersionScheme
              else assumedVersionSchemeJava

            if (hasIncompatibleVersionForScheme(assumedScheme)) {
              val eviction = EvictionErrorPair(
                p.name,
                p.organization,
                p.winner.map(_.module.revision),
                p.evicteds.map(_.module.revision).distinct,
                callers(p.winner, p.evicteds),
                assumedScheme
              )
              if (!assumedIncompatibleEvictions.contains(eviction)) {
                assumedIncompatibleEvictions += eviction
              }
            }
        }

      case _ => ()
    }

    new EvictionError(
      incompatibleEvictions.toList,
      assumedIncompatibleEvictions.toList,
    )
  }

  given evictionErrorLines: ShowLines[EvictionError] = ShowLines { (a: EvictionError) =>
    a.toLines
  }
}

private final case class EvictionErrorPair(
    name: String,
    organization: String,
    winnerRevision: Option[String],
    evictedRevisions: Vector[String],
    callers: List[(ModuleID, String)],
    scheme: String
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

  def toLines(evictions: Seq[EvictionErrorPair], assumed: Boolean): List[String] = {
    val out: mutable.ListBuffer[String] = mutable.ListBuffer()
    out += "found version conflict(s) in library dependencies; some are suspected to be binary incompatible:"
    out += ""
    evictions.foreach({ a =>
      val seen: mutable.Set[ModuleID] = mutable.Set()

      val callers: List[String] = a.callers.map { case (caller, rev) =>
        f"\t    +- ${caller}%-50s (depends on $rev)"
      }
      val que = if (assumed) "?" else ""
      val evictedRevs =
        if (a.evictedRevisions.size <= 1) a.evictedRevisions.mkString
        else a.evictedRevisions.mkString("{", ", ", "}")

      val winnerRev = a.winnerRevision match {
        case Some(r) => s":${r} (${a.scheme}$que) is selected over ${evictedRevs}"
        case _       => " is evicted for all versions"
      }
      val title = s"\t* ${a.organization}:${a.name}$winnerRev"
      val lines = title :: callers.reverse ::: List("")
      out ++= lines
    })
    out.toList
  }
}
