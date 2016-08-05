package coursier.core

import java.util.regex.Pattern.quote
import coursier.core.compatibility._

object Parse {

  def version(s: String): Option[Version] = {
    if (s.isEmpty || s.exists(c => c != '.' && c != '-' && c != '_' && !c.letterOrDigit)) None
    else Some(Version(s))
  }

  def ivyLatestSubRevisionInterval(s: String): Option[VersionInterval] =
    if (s.endsWith(".+")) {
      for {
        from <- version(s.stripSuffix(".+"))
        if from.rawItems.nonEmpty
        last <- Some(from.rawItems.last).collect { case n: Version.Numeric => n }
        // a bit loose, but should do the job
        if from.repr.endsWith(last.repr)
        // appending -a1 to the next version, so has not to include things like
        // nextVersion-RC1 in the interval - nothing like nextVersion* should be included
        to <- version(from.repr.stripSuffix(last.repr) + last.next.repr + "-a1")
        // the contrary would mean something went wrong in the loose substitution above
        if from.rawItems.init == to.rawItems.dropRight(2).init
        if to.rawItems.takeRight(2) == Seq(Version.Literal("a"), Version.Number(1))
      } yield VersionInterval(Some(from), Some(to), fromIncluded = true, toIncluded = false)
    } else
      None

  def versionInterval(s: String): Option[VersionInterval] = {

    def parseBounds(fromIncluded: Boolean, toIncluded: Boolean, s: String) = {

      val commaIdx = s.indexOf(',')

      if (commaIdx >= 0) {
        val strFrom = s.take(commaIdx)
        val strTo = s.drop(commaIdx + 1)

        for {
          from <- if (strFrom.isEmpty) Some(None) else version(strFrom).map(Some(_))
          to <- if (strTo.isEmpty) Some(None) else version(strTo).map(Some(_))
        } yield VersionInterval(from.filterNot(_.isEmpty), to.filterNot(_.isEmpty), fromIncluded, toIncluded)
      } else if (s.nonEmpty && fromIncluded && toIncluded)
        for (v <- version(s) if !v.isEmpty)
          yield VersionInterval(Some(v), Some(v), fromIncluded, toIncluded)
      else
        None
    }

    for {
      fromIncluded <- if (s.startsWith("[")) Some(true) else if (s.startsWith("(")) Some(false) else None
      toIncluded <- if (s.endsWith("]")) Some(true) else if (s.endsWith(")")) Some(false) else None
      s0 = s.drop(1).dropRight(1)
      itv <- parseBounds(fromIncluded, toIncluded, s0)
    } yield itv
  }

  def versionConstraint(s: String): Option[VersionConstraint] = {
    def noConstraint = if (s.isEmpty) Some(VersionConstraint.all) else None

    noConstraint
      .orElse(ivyLatestSubRevisionInterval(s).map(VersionConstraint.interval))
      .orElse(version(s).map(VersionConstraint.preferred))
      .orElse(versionInterval(s).map(VersionConstraint.interval))
  }

  val fallbackConfigRegex = {
    val noPar = "([^" + quote("()") + "]*)"
    "^" + noPar + quote("(") + noPar + quote(")") + "$"
  }.r

  def withFallbackConfig(config: String): Option[(String, String)] =
    Parse.fallbackConfigRegex.findAllMatchIn(config).toSeq match {
      case Seq(m) =>
        assert(m.groupCount == 2)
        val main = config.substring(m.start(1), m.end(1))
        val fallback = config.substring(m.start(2), m.end(2))
        Some((main, fallback))
      case _ =>
        None
    }

}
