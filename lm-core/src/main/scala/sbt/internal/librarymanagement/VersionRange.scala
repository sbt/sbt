package sbt
package internal
package librarymanagement

import sbt.librarymanagement.VersionNumber

object VersionRange {

  /** True if the revision is an ivy-range, not a complete revision. */
  def isVersionRange(revision: String): Boolean = {
    (revision.endsWith("+")) ||
    (revision.contains("[")) ||
    (revision.contains("]")) ||
    (revision.contains("(")) ||
    (revision.contains(")"))
  }

  /**
   * Checks if a version satisfies a version range.
   * @param version The version to check (e.g., "4.2.1")
   * @param range The version range (e.g., "[4.1.0,5)" or "[1.0,2.0]")
   * @return true if version is within the range, false otherwise
   */
  def versionSatisfiesRange(version: String, range: String): Boolean = {
    if (!isVersionRange(range)) {
      // Not a range, just compare directly
      version == range
    } else if (range.endsWith("+")) {
      // Handle plus ranges like "1.0+" meaning >= 1.0
      val base = range.dropRight(1)
      compareVersions(version, base) >= 0
    } else if (hasMavenVersionRange(range)) {
      // Parse Maven-style range like [1.0,2.0) or (1.0,2.0]
      parseMavenRange(range) match {
        case Some((lowerBound, lowerInclusive, upperBound, upperInclusive)) =>
          val lowerOk = lowerBound match {
            case Some(lb) =>
              val cmp = compareVersions(version, lb)
              if (lowerInclusive) cmp >= 0 else cmp > 0
            case None => true
          }
          val upperOk = upperBound match {
            case Some(ub) =>
              val cmp = compareVersions(version, ub)
              if (upperInclusive) cmp <= 0 else cmp < 0
            case None => true
          }
          lowerOk && upperOk
        case None => false
      }
    } else {
      false
    }
  }

  /**
   * Parses a Maven-style version range.
   * @return Some((lowerBound, lowerInclusive, upperBound, upperInclusive)) or None
   */
  private def parseMavenRange(
      range: String
  ): Option[(Option[String], Boolean, Option[String], Boolean)] = {
    val trimmed = range.trim
    if (trimmed.length < 2) None
    else {
      val startChar = trimmed.head
      val endChar = trimmed.last

      val lowerInclusive = startChar == '['
      val upperInclusive = endChar == ']'

      if (!Set('[', '(').contains(startChar) || !Set(']', ')').contains(endChar)) {
        None
      } else {
        val inner = trimmed.substring(1, trimmed.length - 1)
        val commaIdx = inner.indexOf(',')

        if (commaIdx < 0) {
          // Single version constraint like [1.0] means exactly 1.0
          val v = inner.trim
          if (v.nonEmpty) Some((Some(v), true, Some(v), true))
          else None
        } else {
          val lower = inner.substring(0, commaIdx).trim
          val upper = inner.substring(commaIdx + 1).trim
          Some(
            (
              if (lower.nonEmpty) Some(lower) else None,
              lowerInclusive,
              if (upper.nonEmpty) Some(upper) else None,
              upperInclusive
            )
          )
        }
      }
    }
  }

  /**
   * Compares two version strings.
   * @return negative if v1 < v2, 0 if v1 == v2, positive if v1 > v2
   */
  private def compareVersions(v1: String, v2: String): Int = {
    val vn1 = VersionNumber(v1)
    val vn2 = VersionNumber(v2)

    // Compare numeric parts first
    val nums1 = vn1.numbers
    val nums2 = vn2.numbers
    val maxLen = math.max(nums1.length, nums2.length)

    val numericComparison = (0 until maxLen).iterator
      .map { i =>
        val n1 = if (i < nums1.length) nums1(i) else 0L
        val n2 = if (i < nums2.length) nums2(i) else 0L
        n1.compare(n2)
      }
      .find(_ != 0)

    numericComparison match {
      case Some(cmp) => cmp
      case None      =>
        // If numeric parts are equal, compare tags (versions with tags are usually pre-releases)
        val tags1 = vn1.tags
        val tags2 = vn2.tags

        // No tags means release version, which is higher than any pre-release
        if (tags1.isEmpty && tags2.nonEmpty) 1
        else if (tags1.nonEmpty && tags2.isEmpty) -1
        else {
          // Compare tags lexicographically
          val tagMaxLen = math.max(tags1.length, tags2.length)
          val tagComparison = (0 until tagMaxLen).iterator
            .map { i =>
              val t1 = if (i < tags1.length) tags1(i) else ""
              val t2 = if (i < tags2.length) tags2(i) else ""
              t1.compare(t2)
            }
            .find(_ != 0)
          tagComparison.getOrElse(0)
        }
    }
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
        // we assume version ranges never go beyond 5 significant digits.
        case NumPlusPattern(tail) => (0 until maxDigit).map(plusRange(tail, _)).mkString(",")
        case DotNumPlusPattern(base, tail) =>
          (0 until maxDigit).map(plusRange(base + "." + tail, _)).mkString(",")
        case rev if rev.endsWith("+") =>
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

  private val startSym = Set(']', '[', '(')
  private val stopSym = Set(']', '[', ')')
  private val MavenVersionSetPattern =
    """([\]\[\(])([\w\.\-]+)?(,)?([\w\.\-]+)?([\]\[\)])(,.+)?""".r
}
