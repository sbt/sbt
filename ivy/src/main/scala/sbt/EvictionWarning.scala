package sbt

import collection.mutable
import Configurations.Compile
import ScalaArtifacts.{ LibraryID, CompilerID }

final class EvictionWarningOptions private[sbt] (
    val configurations: Seq[Configuration],
    val warnScalaVersionEviction: Boolean,
    val warnDirectEvictions: Boolean,
    val warnTransitiveEvictions: Boolean,
    val showCallers: Boolean,
    val guessCompatible: Function1[(ModuleID, Option[ModuleID], Option[IvyScala]), Boolean]) {
  private[sbt] def configStrings = configurations map { _.name }

  def withConfigurations(configurations: Seq[Configuration]): EvictionWarningOptions =
    copy(configurations = configurations)
  def withWarnScalaVersionEviction(warnScalaVersionEviction: Boolean): EvictionWarningOptions =
    copy(warnScalaVersionEviction = warnScalaVersionEviction)
  def withWarnDirectEvictions(warnDirectEvictions: Boolean): EvictionWarningOptions =
    copy(warnDirectEvictions = warnDirectEvictions)
  def withWarnTransitiveEvictions(warnTransitiveEvictions: Boolean): EvictionWarningOptions =
    copy(warnTransitiveEvictions = warnTransitiveEvictions)
  def withShowCallers(showCallers: Boolean): EvictionWarningOptions =
    copy(showCallers = showCallers)
  def withGuessCompatible(guessCompatible: Function1[(ModuleID, Option[ModuleID], Option[IvyScala]), Boolean]): EvictionWarningOptions =
    copy(guessCompatible = guessCompatible)

  private[sbt] def copy(configurations: Seq[Configuration] = configurations,
    warnScalaVersionEviction: Boolean = warnScalaVersionEviction,
    warnDirectEvictions: Boolean = warnDirectEvictions,
    warnTransitiveEvictions: Boolean = warnTransitiveEvictions,
    showCallers: Boolean = showCallers,
    guessCompatible: Function1[(ModuleID, Option[ModuleID], Option[IvyScala]), Boolean] = guessCompatible): EvictionWarningOptions =
    new EvictionWarningOptions(configurations = configurations,
      warnScalaVersionEviction = warnScalaVersionEviction,
      warnDirectEvictions = warnDirectEvictions,
      warnTransitiveEvictions = warnTransitiveEvictions,
      showCallers = showCallers,
      guessCompatible = guessCompatible)
}

object EvictionWarningOptions {
  def default: EvictionWarningOptions =
    new EvictionWarningOptions(Vector(Compile), true, true, false, false, defaultGuess)
  def full: EvictionWarningOptions =
    new EvictionWarningOptions(Vector(Compile), true, true, true, true, defaultGuess)

  lazy val defaultGuess: Function1[(ModuleID, Option[ModuleID], Option[IvyScala]), Boolean] =
    guessSecondSegment orElse guessSemVer orElse guessFalse
  lazy val guessSecondSegment: PartialFunction[(ModuleID, Option[ModuleID], Option[IvyScala]), Boolean] = {
    case (m1, Some(m2), Some(ivyScala)) if m2.name.endsWith("_" + ivyScala.scalaFullVersion) || m2.name.endsWith("_" + ivyScala.scalaBinaryVersion) =>
      (m1.revision, m2.revision) match {
        case (VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2)) =>
          VersionNumber.SecondSegment.isCompatible(VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2))
        case _ => false
      }
  }
  lazy val guessSemVer: PartialFunction[(ModuleID, Option[ModuleID], Option[IvyScala]), Boolean] = {
    case (m1, Some(m2), _) =>
      (m1.revision, m2.revision) match {
        case (VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2)) =>
          VersionNumber.SemVer.isCompatible(VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2))
        case _ => false
      }
  }
  lazy val guessFalse: PartialFunction[(ModuleID, Option[ModuleID], Option[IvyScala]), Boolean] = {
    case (_, _, _) => false
  }
}

final class EvictionPair private[sbt] (
    val organization: String,
    val name: String,
    val winner: Option[ModuleReport],
    val evicteds: Seq[ModuleReport],
    val includesDirect: Boolean,
    val showCallers: Boolean) {
  override def toString: String =
    EvictionPair.evictionPairLines.showLines(this).mkString
}

object EvictionPair {
  implicit val evictionPairLines: ShowLines[EvictionPair] = ShowLines { a: EvictionPair =>
    val revs = a.evicteds map { _.module.revision }
    val revsStr = if (revs.size <= 1) revs.mkString else "(" + revs.mkString(", ") + ")"
    val winnerRev = (a.winner map { r =>
      val callers: String =
        if (a.showCallers)
          r.callers match {
            case Seq() => ""
            case cs    => (cs map { _.caller.toString }).mkString(" (caller: ", ", ", ")")
          }
        else ""
      r.module.revision + callers
    }).headOption map { " -> " + _ } getOrElse ""
    Seq(s"\t* ${a.organization}:${a.name}:${revsStr}$winnerRev")
  }
}

final class EvictionWarning private[sbt] (
    val options: EvictionWarningOptions,
    val scalaEvictions: Seq[EvictionPair],
    val directEvictions: Seq[EvictionPair],
    val transitiveEvictions: Seq[EvictionPair],
    val allEvictions: Seq[EvictionPair]) {
  def reportedEvictions: Seq[EvictionPair] = scalaEvictions ++ directEvictions ++ transitiveEvictions
}

object EvictionWarning {
  def apply(module: IvySbt#Module, options: EvictionWarningOptions, report: UpdateReport, log: Logger): EvictionWarning = {
    val evictions = buildEvictions(options, report)
    processEvictions(module, options, evictions)
  }

  private[sbt] def buildEvictions(options: EvictionWarningOptions, report: UpdateReport): Seq[OrganizationArtifactReport] = {
    val buffer: mutable.ListBuffer[OrganizationArtifactReport] = mutable.ListBuffer()
    val confs = report.configurations filter { x => options.configStrings contains x.configuration }
    confs flatMap { confReport =>
      confReport.details map { detail =>
        if ((detail.modules exists { _.evicted }) &&
          !(buffer exists { x => (x.organization == detail.organization) && (x.name == detail.name) })) {
          buffer += detail
        }
      }
    }
    buffer.toList.toVector
  }

  private[sbt] def isScalaArtifact(module: IvySbt#Module, organization: String, name: String): Boolean =
    module.moduleSettings.ivyScala match {
      case Some(s) =>
        organization == s.scalaOrganization &&
          (name == LibraryID) || (name == CompilerID)
      case _ => false
    }

  private[sbt] def processEvictions(module: IvySbt#Module, options: EvictionWarningOptions, reports: Seq[OrganizationArtifactReport]): EvictionWarning = {
    val directDependencies = module.moduleSettings match {
      case x: InlineConfiguration => x.dependencies
      case _                      => Seq()
    }
    val pairs = reports map { detail =>
      val evicteds = detail.modules filter { _.evicted }
      val winner = (detail.modules filterNot { _.evicted }).headOption
      val includesDirect: Boolean =
        options.warnDirectEvictions &&
          (directDependencies exists { dep =>
            (detail.organization == dep.organization) && (detail.name == dep.name)
          })
      new EvictionPair(detail.organization, detail.name, winner, evicteds, includesDirect, options.showCallers)
    }
    val scalaEvictions: mutable.ListBuffer[EvictionPair] = mutable.ListBuffer()
    val directEvictions: mutable.ListBuffer[EvictionPair] = mutable.ListBuffer()
    val transitiveEvictions: mutable.ListBuffer[EvictionPair] = mutable.ListBuffer()
    def guessCompatible(p: EvictionPair): Boolean =
      p.evicteds forall { r =>
        options.guessCompatible((r.module, p.winner map { _.module }, module.moduleSettings.ivyScala))
      }
    pairs foreach {
      case p if isScalaArtifact(module, p.organization, p.name) =>
        (module.moduleSettings.ivyScala, p.winner) match {
          case (Some(s), Some(winner)) if ((s.scalaFullVersion != winner.module.revision) && options.warnScalaVersionEviction) =>
            scalaEvictions += p
          case _ =>
        }
      case p if p.includesDirect =>
        if (!guessCompatible(p) && options.warnDirectEvictions) {
          directEvictions += p
        }
      case p =>
        if (!guessCompatible(p) && options.warnTransitiveEvictions) {
          transitiveEvictions += p
        }
    }
    new EvictionWarning(options, scalaEvictions.toList,
      directEvictions.toList, transitiveEvictions.toList, pairs)
  }

  implicit val evictionWarningLines: ShowLines[EvictionWarning] = ShowLines { a: EvictionWarning =>
    import ShowLines._
    val out: mutable.ListBuffer[String] = mutable.ListBuffer()
    if (!a.scalaEvictions.isEmpty) {
      out += "Scala version was updated by one of library dependencies:"
      out ++= (a.scalaEvictions flatMap { _.lines })
      out += "To force scalaVersion, add the following:"
      out += "\tivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }"
    }

    if (!a.directEvictions.isEmpty || !a.transitiveEvictions.isEmpty) {
      out += "There may be incompatibilities among your library dependencies."
      out += "Here are some of the libraries that were evicted:"
      out ++= (a.directEvictions flatMap { _.lines })
      out ++= (a.transitiveEvictions flatMap { _.lines })
    }

    if (!a.allEvictions.isEmpty && !a.reportedEvictions.isEmpty && !a.options.showCallers) {
      out += "Run 'evicted' to see detailed eviction warnings"
    }

    out.toList
  }
}
