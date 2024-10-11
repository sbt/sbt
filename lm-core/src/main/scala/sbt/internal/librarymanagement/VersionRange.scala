package sbt
package internal
package librarymanagement

object VersionRange {

  /** True if the revision is an ivy-range, not a complete revision. */
  def isVersionRange(revision: String): Boolean = {
    (revision endsWith "+") ||
    (revision contains "[") ||
    (revision contains "]") ||
    (revision contains "(") ||
    (revision contains ")")
  }

  // Assuming Ivy is used to resolve conflict, this removes the version range
  // when it is open-ended to avoid dependency resolution hitting the Internet to get the latest.
  // See https://github.com/sbt/sbt/issues/2954
  def stripMavenVersionRange(version: String): Option[String] =
    if (isVersionRange(version)) {
      val noSpace = version.replace(" ", "")
      noSpace match {
        case MavenVersionSetPattern(open1, x1, comma, x2, close1, _) =>
          // http://maven.apache.org/components/enforcer/enforcer-rules/versionRanges.html
          (open1, Option(x1), Option(comma), Option(x2), close1) match {
            case (_, None, _, Some(x2), "]") => Some(x2)
            // a good upper bound is unknown
            case (_, None, _, Some(_), ")") => None
            case (_, Some(x1), _, None, _)  => Some(x1)
            case _                          => None
          }
        case _ => None
      }
    } else None

  /** Converts Ivy revision ranges to that of Maven POM */
  def fromIvyToMavenVersion(revision: String): String = {
    def plusRange(s: String, shift: Int = 0) = {
      def pow(i: Int): Int = if (i > 0) 10 * pow(i - 1) else 1
      val (prefixVersion, lastVersion) = (s + "0" * shift).reverse.split("\\.", 2) match {
        case Array(revLast, revRest) =>
          (revRest.reverse + ".", revLast.reverse)
        case Array(revLast) => ("", revLast.reverse)
      }
      val lastVersionInt = lastVersion.toInt
      s"[${prefixVersion}${lastVersion},${prefixVersion}${lastVersionInt + pow(shift)})"
    }
    val DotPlusPattern = """(.+)\.\+""".r
    val DotNumPlusPattern = """(.+)\.(\d+)\+""".r
    val NumPlusPattern = """(\d+)\+""".r
    val maxDigit = 5
    try {
      revision match {
        case "+"                  => "[0,)"
        case DotPlusPattern(base) => plusRange(base)
        // This is a heuristic.  Maven just doesn't support Ivy's notions of 1+, so
        // we assume version ranges never go beyond 5 siginificant digits.
        case NumPlusPattern(tail) => (0 until maxDigit).map(plusRange(tail, _)).mkString(",")
        case DotNumPlusPattern(base, tail) =>
          (0 until maxDigit).map(plusRange(base + "." + tail, _)).mkString(",")
        case rev if rev endsWith "+" =>
          sys.error(s"dynamic revision '$rev' cannot be translated to POM")
        case rev if startSym(rev(0)) && stopSym(rev(rev.length - 1)) =>
          val start = rev(0)
          val stop = rev(rev.length - 1)
          val mid = rev.substring(1, rev.length - 1)
          (if (start == ']') "(" else start.toString) + mid + (if (stop == '[') ")" else stop)
        case _ => revision
      }
    } catch {
      case _: NumberFormatException =>
        // TODO - if the version doesn't meet our expectations, maybe we just issue a hard
        //        error instead of softly ignoring the attempt to rewrite.
        // sys.error(s"Could not fix version [$revision] into maven style version")
        revision
    }
  }

  def hasMavenVersionRange(version: String): Boolean =
    if (version.length <= 1) false
    else startSym(version(0)) && stopSym(version(version.length - 1))

  private[this] val startSym = Set(']', '[', '(')
  private[this] val stopSym = Set(']', '[', ')')
  private[this] val MavenVersionSetPattern =
    """([\]\[\(])([\w\.\-]+)?(,)?([\w\.\-]+)?([\]\[\)])(,.+)?""".r
}
