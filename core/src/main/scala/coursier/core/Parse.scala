package coursier.core

import coursier.core.compatibility._

object Parse {

  def scope(s: String): Scope = s match {
    case "compile" => Scope.Compile
    case "runtime" => Scope.Runtime
    case "test" => Scope.Test
    case "provided" => Scope.Provided
    case "import" => Scope.Import
    case other => Scope.Other(other)
  }

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
    } yield VersionInterval(from.filterNot(_.cmp.isEmpty), to.filterNot(_.cmp.isEmpty), fromIncluded, toIncluded)
  }

  def versionConstraint(s: String): Option[VersionConstraint] = {
    def noConstraint = if (s.isEmpty) Some(VersionConstraint.None) else None

    noConstraint
      .orElse(version(s).map(VersionConstraint.Preferred))
      .orElse(versionInterval(s).map(VersionConstraint.Interval))
  }

}
