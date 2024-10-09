package sbt.librarymanagement

import collection.mutable
import Configurations.Compile
import ScalaArtifacts.{ LibraryID, CompilerID }
import sbt.internal.librarymanagement.VersionSchemes
import sbt.util.Logger
import sbt.util.ShowLines

final class EvictionWarningOptions private[sbt] (
    val configurations: Seq[ConfigRef],
    val warnScalaVersionEviction: Boolean,
    val warnDirectEvictions: Boolean,
    val warnTransitiveEvictions: Boolean,
    val warnEvictionSummary: Boolean,
    val infoAllEvictions: Boolean,
    val showCallers: Boolean,
    val guessCompatible: Function1[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean]
) {
  def withConfigurations(configurations: Seq[ConfigRef]): EvictionWarningOptions =
    copy(configurations = configurations)
  def withWarnScalaVersionEviction(warnScalaVersionEviction: Boolean): EvictionWarningOptions =
    copy(warnScalaVersionEviction = warnScalaVersionEviction)
  def withWarnDirectEvictions(warnDirectEvictions: Boolean): EvictionWarningOptions =
    copy(warnDirectEvictions = warnDirectEvictions)
  def withWarnTransitiveEvictions(warnTransitiveEvictions: Boolean): EvictionWarningOptions =
    copy(warnTransitiveEvictions = warnTransitiveEvictions)
  def withWarnEvictionSummary(warnEvictionSummary: Boolean): EvictionWarningOptions =
    copy(warnEvictionSummary = warnEvictionSummary)
  def withInfoAllEvictions(infoAllEvictions: Boolean): EvictionWarningOptions =
    copy(infoAllEvictions = infoAllEvictions)
  def withShowCallers(showCallers: Boolean): EvictionWarningOptions =
    copy(showCallers = showCallers)
  def withGuessCompatible(
      guessCompatible: Function1[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean]
  ): EvictionWarningOptions =
    copy(guessCompatible = guessCompatible)

  private[sbt] def copy(
      configurations: Seq[ConfigRef] = configurations,
      warnScalaVersionEviction: Boolean = warnScalaVersionEviction,
      warnDirectEvictions: Boolean = warnDirectEvictions,
      warnTransitiveEvictions: Boolean = warnTransitiveEvictions,
      warnEvictionSummary: Boolean = warnEvictionSummary,
      infoAllEvictions: Boolean = infoAllEvictions,
      showCallers: Boolean = showCallers,
      guessCompatible: Function1[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] =
        guessCompatible
  ): EvictionWarningOptions =
    new EvictionWarningOptions(
      configurations = configurations,
      warnScalaVersionEviction = warnScalaVersionEviction,
      warnDirectEvictions = warnDirectEvictions,
      warnTransitiveEvictions = warnTransitiveEvictions,
      warnEvictionSummary = warnEvictionSummary,
      infoAllEvictions = infoAllEvictions,
      showCallers = showCallers,
      guessCompatible = guessCompatible
    )
}

object EvictionWarningOptions {
  def empty: EvictionWarningOptions =
    new EvictionWarningOptions(
      Vector(),
      warnScalaVersionEviction = false,
      warnDirectEvictions = false,
      warnTransitiveEvictions = false,
      warnEvictionSummary = false,
      infoAllEvictions = false,
      showCallers = false,
      defaultGuess
    )
  def default: EvictionWarningOptions = summary
  def full: EvictionWarningOptions =
    new EvictionWarningOptions(
      Vector(Compile),
      warnScalaVersionEviction = true,
      warnDirectEvictions = true,
      warnTransitiveEvictions = true,
      warnEvictionSummary = false,
      infoAllEvictions = true,
      showCallers = true,
      defaultGuess
    )
  def summary: EvictionWarningOptions =
    new EvictionWarningOptions(
      Vector(Compile),
      warnScalaVersionEviction = false,
      warnDirectEvictions = false,
      warnTransitiveEvictions = false,
      warnEvictionSummary = true,
      infoAllEvictions = false,
      showCallers = false,
      defaultGuess
    )

  lazy val defaultGuess: Function1[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] =
    guessSbtOne orElse guessSecondSegment orElse guessSemVer orElse guessFalse

  private[sbt] def isNameScalaSuffixed(name: String): Boolean =
    name.contains("_2.") || name.contains("_3") || name.contains("_4")

  /** A partial function that checks if given m2 is suffixed, and use pvp to evaluate. */
  lazy val guessSecondSegment
      : PartialFunction[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] = {
    case (m1, Some(m2), Some(_)) if isNameScalaSuffixed(m2.name) =>
      (m1.revision, m2.revision) match {
        case (VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2)) =>
          VersionNumber.SecondSegment
            .isCompatible(VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2))
        case _ => false
      }
  }

  /** A partial function that checks two versions match pvp. */
  private[sbt] lazy val evalPvp
      : PartialFunction[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] = {
    case (m1, Some(m2), _) =>
      (m1.revision, m2.revision) match {
        case (VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2)) =>
          VersionNumber.SecondSegment
            .isCompatible(VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2))
        case _ => false
      }
  }

  lazy val guessSbtOne
      : PartialFunction[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] = {
    case (m1, Some(m2), Some(scalaModuleInfo))
        if (m2.organization == "org.scala-sbt") &&
          (m2.name.endsWith("_" + scalaModuleInfo.scalaFullVersion) ||
            m2.name.endsWith("_" + scalaModuleInfo.scalaBinaryVersion)) =>
      (m1.revision, m2.revision) match {
        case (VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2)) =>
          VersionNumber.SemVer
            .isCompatible(VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2))
        case _ => false
      }
  }

  lazy val guessSemVer
      : PartialFunction[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] = {
    case (m1, Some(m2), _) =>
      (m1.revision, m2.revision) match {
        case (VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2)) =>
          VersionNumber.SemVer
            .isCompatible(VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2))
        case _ => false
      }
  }

  lazy val guessEarlySemVer
      : PartialFunction[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] = {
    case (m1, Some(m2), _) =>
      (m1.revision, m2.revision) match {
        case (VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2)) =>
          VersionNumber.EarlySemVer
            .isCompatible(VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2))
        case _ => false
      }
  }

  lazy val guessStrict
      : PartialFunction[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] = {
    case (m1, Some(m2), _) =>
      (m1.revision, m2.revision) match {
        case (VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2)) =>
          VersionNumber.Strict
            .isCompatible(VersionNumber(ns1, ts1, es1), VersionNumber(ns2, ts2, es2))
        case _ => false
      }
  }

  lazy val guessFalse
      : PartialFunction[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] = {
    case (_, _, _) => false
  }

  lazy val guessTrue
      : PartialFunction[(ModuleID, Option[ModuleID], Option[ScalaModuleInfo]), Boolean] = {
    case (_, _, _) => true
  }
}

final class EvictionPair private[sbt] (
    val organization: String,
    val name: String,
    val winner: Option[ModuleReport],
    val evicteds: Vector[ModuleReport],
    val includesDirect: Boolean,
    val showCallers: Boolean
) {
  val evictedRevs: String = {
    val revs = evicteds map { _.module.revision }
    if (revs.size <= 1) revs.mkString else revs.distinct.mkString("{", ", ", "}")
  }

  override def toString: String =
    EvictionPair.evictionPairLines.showLines(this).mkString
  override def equals(o: Any): Boolean = o match {
    case o: EvictionPair =>
      (this.organization == o.organization) &&
      (this.name == o.name)
    case _ => false
  }
  override def hashCode: Int = {
    var hash = 1
    hash = hash * 31 + this.organization.##
    hash = hash * 31 + this.name.##
    hash
  }
}

object EvictionPair {
  implicit val evictionPairLines: ShowLines[EvictionPair] = ShowLines { (a: EvictionPair) =>
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
    val winnerRev = a.winner match {
      case Some(r) => s":${r.module.revision} is selected over ${a.evictedRevs}"
      case _       => " is evicted for all versions"
    }
    val title = s"\t* ${a.organization}:${a.name}$winnerRev"
    title :: (if (a.showCallers) callers.reverse else Nil) ::: List("")
  }
}

final class EvictionWarning private[sbt] (
    val options: EvictionWarningOptions,
    val scalaEvictions: Seq[EvictionPair],
    val directEvictions: Seq[EvictionPair],
    val transitiveEvictions: Seq[EvictionPair],
    val allEvictions: Seq[EvictionPair],
    val binaryIncompatibleEvictionExists: Boolean
) {
  private[sbt] def this(
      options: EvictionWarningOptions,
      scalaEvictions: Seq[EvictionPair],
      directEvictions: Seq[EvictionPair],
      transitiveEvictions: Seq[EvictionPair],
      allEvictions: Seq[EvictionPair]
  ) = this(options, scalaEvictions, directEvictions, transitiveEvictions, allEvictions, false)
  def reportedEvictions: Seq[EvictionPair] =
    scalaEvictions ++ directEvictions ++ transitiveEvictions
  private[sbt] def infoAllTheThings: List[String] = EvictionWarning.infoAllTheThings(this)
}

object EvictionWarning {
  @deprecated("Use variant that doesn't take an unused logger", "1.2.0")
  def apply(
      module: ModuleDescriptor,
      options: EvictionWarningOptions,
      report: UpdateReport,
      log: Logger
  ): EvictionWarning = apply(module, options, report)

  def apply(
      module: ModuleDescriptor,
      options: EvictionWarningOptions,
      report: UpdateReport
  ): EvictionWarning = {
    val evictions = buildEvictions(options, report)
    processEvictions(module, options, evictions)
  }

  private[sbt] def buildEvictions(
      options: EvictionWarningOptions,
      report: UpdateReport
  ): Seq[OrganizationArtifactReport] = {
    val buffer: mutable.ListBuffer[OrganizationArtifactReport] = mutable.ListBuffer()
    val confs = report.configurations filter { x =>
      options.configurations.contains[ConfigRef](x.configuration)
    }
    confs flatMap { confReport =>
      confReport.details map { detail =>
        if (
          (detail.modules exists { _.evicted }) &&
          !(buffer exists { x =>
            (x.organization == detail.organization) && (x.name == detail.name)
          })
        ) {
          buffer += detail
        }
      }
    }
    buffer.toList.toVector
  }

  private[sbt] def isScalaArtifact(
      module: ModuleDescriptor,
      organization: String,
      name: String
  ): Boolean =
    module.scalaModuleInfo match {
      case Some(s) =>
        organization == s.scalaOrganization &&
        (name == LibraryID) || (name == CompilerID)
      case _ => false
    }

  private[sbt] def processEvictions(
      module: ModuleDescriptor,
      options: EvictionWarningOptions,
      reports: Seq[OrganizationArtifactReport]
  ): EvictionWarning = {
    val directDependencies = module.directDependencies
    val pairs = reports map { detail =>
      val evicteds = detail.modules filter { _.evicted }
      val winner = (detail.modules filterNot { _.evicted }).headOption
      val includesDirect: Boolean =
        options.warnDirectEvictions &&
          (directDependencies exists { dep =>
            (detail.organization == dep.organization) && (detail.name == dep.name)
          })
      new EvictionPair(
        detail.organization,
        detail.name,
        winner,
        evicteds,
        includesDirect,
        options.showCallers
      )
    }
    val scalaEvictions: mutable.ListBuffer[EvictionPair] = mutable.ListBuffer()
    val directEvictions: mutable.ListBuffer[EvictionPair] = mutable.ListBuffer()
    val transitiveEvictions: mutable.ListBuffer[EvictionPair] = mutable.ListBuffer()
    var binaryIncompatibleEvictionExists = false
    def guessCompatible(p: EvictionPair): Boolean =
      p.evicteds forall { r =>
        val winnerOpt = p.winner map { _.module }
        val extraAttributes = ((p.winner match {
          case Some(r) => r.extraAttributes
          case _       => Map.empty
        }): collection.immutable.Map[String, String]) ++ (winnerOpt match {
          case Some(w) => w.extraAttributes
          case _       => Map.empty
        })
        val schemeOpt = VersionSchemes.extractFromExtraAttributes(extraAttributes)
        val f = (winnerOpt, schemeOpt) match {
          case (Some(_), Some(VersionSchemes.Always)) =>
            EvictionWarningOptions.guessTrue
          case (Some(_), Some(VersionSchemes.Strict)) =>
            EvictionWarningOptions.guessStrict
          case (Some(_), Some(VersionSchemes.EarlySemVer)) =>
            EvictionWarningOptions.guessEarlySemVer
          case (Some(_), Some(VersionSchemes.SemVerSpec)) =>
            EvictionWarningOptions.guessSemVer
          case (Some(_), Some(VersionSchemes.PackVer)) =>
            EvictionWarningOptions.evalPvp
          case _ => options.guessCompatible(_)
        }
        f((r.module, winnerOpt, module.scalaModuleInfo))
      }
    pairs foreach {
      case p if isScalaArtifact(module, p.organization, p.name) =>
        (module.scalaModuleInfo, p.winner) match {
          case (Some(s), Some(winner)) if (s.scalaFullVersion != winner.module.revision) =>
            if (options.warnScalaVersionEviction)
              scalaEvictions += p
            if (options.warnEvictionSummary)
              binaryIncompatibleEvictionExists = true
          case _ =>
        }
      case p if p.includesDirect =>
        if (!guessCompatible(p)) {
          if (options.warnDirectEvictions)
            directEvictions += p
          if (options.warnEvictionSummary)
            binaryIncompatibleEvictionExists = true
        }
      case p =>
        // don't report on a transitive eviction that does not have a winner
        // https://github.com/sbt/sbt/issues/4946
        if (!guessCompatible(p) && p.winner.isDefined) {
          if (options.warnTransitiveEvictions)
            transitiveEvictions += p
          if (options.warnEvictionSummary)
            binaryIncompatibleEvictionExists = true
        }
    }
    new EvictionWarning(
      options,
      scalaEvictions.toList,
      directEvictions.toList,
      transitiveEvictions.toList,
      pairs,
      binaryIncompatibleEvictionExists
    )
  }

  given evictionWarningLines: ShowLines[EvictionWarning] = ShowLines { (a: EvictionWarning) =>
    import ShowLines._
    val out: mutable.ListBuffer[String] = mutable.ListBuffer()
    if (a.options.warnEvictionSummary && a.binaryIncompatibleEvictionExists) {
      out += "There may be incompatibilities among your library dependencies; run 'evicted' to see detailed eviction warnings."
    }

    if (a.scalaEvictions.nonEmpty) {
      out += "Scala version was updated by one of library dependencies:"
      out ++= (a.scalaEvictions flatMap { _.lines })
      out += "To force scalaVersion, add the following:"
      out += "\tscalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true)))"
    }

    if (a.directEvictions.nonEmpty || a.transitiveEvictions.nonEmpty) {
      out += "Found version conflict(s) in library dependencies; some are suspected to be binary incompatible:"
      out += ""
      out ++= (a.directEvictions flatMap { _.lines })
      out ++= (a.transitiveEvictions flatMap { _.lines })
    }

    out.toList
  }

  private[sbt] def infoAllTheThings(a: EvictionWarning): List[String] =
    if (a.options.infoAllEvictions) {
      import ShowLines._
      val evo = a.options
      val out: mutable.ListBuffer[String] = mutable.ListBuffer()
      a.allEvictions foreach { ev =>
        if ((a.scalaEvictions.contains[EvictionPair](ev)) && evo.warnScalaVersionEviction) ()
        else if ((a.directEvictions.contains[EvictionPair](ev)) && evo.warnDirectEvictions) ()
        else if ((a.transitiveEvictions.contains[EvictionPair](ev)) && evo.warnTransitiveEvictions)
          ()
        else {
          out ++= ev.lines
        }
      }
      if (out.isEmpty) Nil
      else List("Here are other dependency conflicts that were resolved:", "") ::: out.toList
    } else Nil
}
