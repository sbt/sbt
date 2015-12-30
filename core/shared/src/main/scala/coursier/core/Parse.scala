package coursier.core

import java.util.regex.Pattern.quote
import coursier.core.compatibility._

object Parse {

  def version(s: String): Option[Version] = {
    if (s.isEmpty || s.exists(c => c != '.' && c != '-' && c != '_' && !c.letterOrDigit)) None
    else Some(Version(s))
  }

  def versionInterval(s: String): Option[VersionInterval] = {
    for {
      fromIncluded <- if (s.startsWith("[")) Some(true) else if (s.startsWith("(")) Some(false) else None
      toIncluded <- if (s.endsWith("]")) Some(true) else if (s.endsWith(")")) Some(false) else None
      s0 = s.drop(1).dropRight(1)
      commaIdx = s0.indexOf(',')
      if commaIdx >= 0
      strFrom = s0.take(commaIdx)
      strTo = s0.drop(commaIdx + 1)
      from <- if (strFrom.isEmpty) Some(None) else version(strFrom).map(Some(_))
      to <- if (strTo.isEmpty) Some(None) else version(strTo).map(Some(_))
    } yield VersionInterval(from.filterNot(_.isEmpty), to.filterNot(_.isEmpty), fromIncluded, toIncluded)
  }

  def versionConstraint(s: String): Option[VersionConstraint] = {
    def noConstraint = if (s.isEmpty) Some(VersionConstraint.None) else None

    noConstraint
      .orElse(version(s).map(VersionConstraint.Preferred))
      .orElse(versionInterval(s).map(VersionConstraint.Interval))
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
