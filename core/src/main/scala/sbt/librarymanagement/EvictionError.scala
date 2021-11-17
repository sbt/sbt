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
    val options = EvictionWarningOptions.full
    val evictions = EvictionWarning.buildEvictions(options, report)
    processEvictions(
      module,
      options,
      evictions,
      schemes,
      assumedVersionScheme,
      assumedVersionSchemeJava,
      assumedEvictionErrorLevel,
    )
  }

  private[sbt] def processEvictions(
      module: ModuleDescriptor,
      options: EvictionWarningOptions,
      reports: Seq[OrganizationArtifactReport],
      schemes: Seq[ModuleID],
      assumedVersionScheme: String,
      assumedVersionSchemeJava: String,
      assumedEvictionErrorLevel: Level.Value,
  ): EvictionError = {
    val directDependencies = module.directDependencies
    val pairs = reports map { detail =>
      val evicteds = detail.modules filter { _.evicted }
      val winner = (detail.modules filterNot { _.evicted }).headOption
      new EvictionPair(
        detail.organization,
        detail.name,
        winner,
        evicteds,
        true,
        options.showCallers
      )
    }
    val incompatibleEvictions: mutable.ListBuffer[(EvictionPair, String)] = mutable.ListBuffer()
    val assumedIncompatEvictions: mutable.ListBuffer[(EvictionPair, String)] = mutable.ListBuffer()
    val sbvOpt = module.scalaModuleInfo.map(_.scalaBinaryVersion)
    val userDefinedSchemes: Map[(String, String), String] = Map(schemes flatMap { s =>
      val organization = s.organization
      VersionSchemes.validateScheme(s.revision)
      val versionScheme = s.revision
      (s.crossVersion, sbvOpt) match {
        case (_: Binary, Some("2.13")) =>
          List(
            (s.organization, s"${s.name}_2.13") -> versionScheme,
            (s.organization, s"${s.name}_3") -> versionScheme
          )
        case (_: Binary, Some(sbv)) if sbv.startsWith("3.0") || sbv == "3" =>
          List(
            (s.organization, s"${s.name}_$sbv") -> versionScheme,
            (s.organization, s"${s.name}_2.13") -> versionScheme
          )
        case (_: Binary, Some(sbv)) =>
          List((s.organization, s"${s.name}_$sbv") -> versionScheme)
        case _ =>
          List((s.organization, s.name) -> versionScheme)
      }
    }: _*)

    def calculateCompatible(p: EvictionPair): (Boolean, String, Boolean, String) = {
      val winnerOpt = p.winner map { _.module }
      val extraAttributes = ((p.winner match {
        case Some(r) => r.extraAttributes.toMap
        case _       => Map.empty
      }): collection.immutable.Map[String, String]) ++ (winnerOpt match {
        case Some(w) => w.extraAttributes.toMap
        case _       => Map.empty
      })
      // prioritize user-defined version scheme to allow overriding the real scheme
      val schemeOpt = userDefinedSchemes
        .get((p.organization, p.name))
        .orElse(userDefinedSchemes.get((p.organization, "*")))
        .orElse(VersionSchemes.extractFromExtraAttributes(extraAttributes))
        .orElse(userDefinedSchemes.get(("*", "*")))
      val f = (winnerOpt, schemeOpt) match {
        case (Some(_), Some(scheme)) => VersionSchemes.evalFunc(scheme)
        case _                       => EvictionWarningOptions.guessTrue
      }
      val scheme =
        if (isNameScalaSuffixed(p.name)) assumedVersionScheme
        else assumedVersionSchemeJava
      val guess = VersionSchemes.evalFunc(scheme)
      (p.evicteds forall { r =>
        f((r.module, winnerOpt, module.scalaModuleInfo))
      }, schemeOpt.getOrElse("?"), p.evicteds forall { r =>
        guess((r.module, winnerOpt, module.scalaModuleInfo))
      }, scheme)
    }
    pairs foreach {
      // don't report on a transitive eviction that does not have a winner
      // https://github.com/sbt/sbt/issues/4946
      case p if p.winner.isDefined =>
        val r = calculateCompatible(p)
        if (!r._1) {
          incompatibleEvictions += (p -> r._2)
        } else if (!r._3) {
          assumedIncompatEvictions += (p -> r._4)
        }
      case _ => ()
    }
    new EvictionError(
      incompatibleEvictions.toList,
      assumedIncompatEvictions.toList,
    )
  }

  implicit val evictionErrorLines: ShowLines[EvictionError] = ShowLines { (a: EvictionError) =>
    a.toLines
  }
}

final class EvictionError private[sbt] (
    val incompatibleEvictions: Seq[(EvictionPair, String)],
    val assumedIncompatibleEvictions: Seq[(EvictionPair, String)],
) {
  def run(): Unit =
    if (incompatibleEvictions.nonEmpty) {
      sys.error(toLines.mkString("\n"))
    }

  def toLines: List[String] = toLines(incompatibleEvictions, false)

  def toAssumedLines: List[String] = toLines(assumedIncompatibleEvictions, true)

  def toLines(evictions: Seq[(EvictionPair, String)], assumed: Boolean): List[String] = {
    val out: mutable.ListBuffer[String] = mutable.ListBuffer()
    out += "found version conflict(s) in library dependencies; some are suspected to be binary incompatible:"
    out += ""
    evictions.foreach({
      case (a, scheme) =>
        val revs = a.evicteds map { _.module.revision }
        val revsStr =
          if (revs.size <= 1) revs.mkString else "{" + revs.distinct.mkString(", ") + "}"
        val seen: mutable.Set[ModuleID] = mutable.Set()
        val callers: List[String] = (a.evicteds.toList ::: a.winner.toList) flatMap { r =>
          val rev = r.module.revision
          r.callers.toList flatMap { caller =>
            if (seen(caller.caller)) Nil
            else {
              seen += caller.caller
              List(f"\t    +- ${caller}%-50s (depends on $rev)")
            }
          }
        }
        val que = if (assumed) "?" else ""
        val winnerRev = a.winner match {
          case Some(r) => s":${r.module.revision} ($scheme$que) is selected over ${revsStr}"
          case _       => " is evicted for all versions"
        }
        val title = s"\t* ${a.organization}:${a.name}$winnerRev"
        val lines = title :: (if (a.showCallers) callers.reverse else Nil) ::: List("")
        out ++= lines
    })
    out.toList
  }
}
