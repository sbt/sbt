package sbt.librarymanagement

import scala.annotation.tailrec

/**
 * Semantic version selector API to check if the VersionNumber satisfies
 * conditions described by semantic version selector.
 */
sealed abstract case class SemanticSelector(
    private val selectors: Seq[SemanticSelector.SemSelAndChunk]) {

  /**
   * Check if the version number satisfies the conditions described by semantic version selector.
   *
   * The empty fields of the version number are assumed to be 0, for example, `1` is treated as `1.0.0`.
   *
   * @param versionNumber The Version Number to be checked if it satisfies the conditions.
   * @return The result of checking the version number satisfies the conditions or not.
   */
  def matches(versionNumber: VersionNumber): Boolean = {
    selectors.exists(_.matches(versionNumber))
  }
  override def toString: String = selectors.map(_.toString).mkString(" || ")
}
object SemanticSelector {

  /**
   * Build a SemanticSelector that can match specific semantic versions.
   *
   * A `comparator` generally consist of an operator and version specifier.
   * The set of operators is
   * - `<`: Less than
   * - `<=`: Less than or equal to
   * - `>`: Greater than
   * - `>=`: Greater than or equal to
   * - `=`: Equal
   * If no operator is specified, `=` is assumed.
   *
   * If minor or patch versions are not specified, some numbers are assumed.
   * - `<=1.0` is equivalent to `<1.1.0`.
   * - `<1.0` is equivalent to `<1.0.0`.
   * - `>=1.0` is equivalent to `>=1.0.0`.
   * - `>1.0` is equivalent to `>=1.1.0`.
   * - `=1.0` is equivalent to `>=1.0 <=1.0` (so `>=1.0.0 <1.1.0`).
   *
   * Comparators can be combined by spaces to form the intersection set of the comparators.
   * For example, `>1.2.3 <4.5.6` matches versions that are `greater than 1.2.3 AND less than 4.5.6`.
   *
   * The (intersection) set of comparators can combined by ` || ` (spaces are required) to form the
   * union set of the intersection sets. So the semantic selector is in disjunctive normal form.
   *
   * Metadata and pre-release of VersionNumber are ignored.
   * So `1.0.0` matches any versions that have `1.0.0` as normal version with any pre-release version
   * or any metadata like `1.0.0-alpha`, `1.0.0+metadata`.
   *
   * Wildcard (`x`, `X`, `*`) can be used to match any number of minor or patch version.
   * Actually, `1.0.x` is equivalent to `=1.0` (that is equivalent to `>=1.0.0 <1.1.0`)
   *
   * The hyphen range like `1.2.3 - 4.5.6` matches inclusive set of versions.
   * So `1.2.3 - 4.5.6` is equivalent to `>=1.2.3 <=4.5.6`.
   * Both sides of comparators around - are required and they can not have any operators.
   * For example, `>=1.2.3 - 4.5.6` is invalid.
   *
   * @param selector A string that represents semantic version selector.
   * @return A `SemanticSelector` that can match only onto specific semantic versions.
   * @throws java.lang.IllegalAccessException when selector is in invalid format of semantic version selector.
   */
  def apply(selector: String): SemanticSelector = {
    val orChunkTokens = selector.split("\\s+\\|\\|\\s+").map(_.trim)
    val orChunks = orChunkTokens.map { chunk =>
      SemSelAndChunk(chunk)
    }
    new SemanticSelector(orChunks) {}
  }

  private[this] sealed trait SemSelOperator
  private[this] case object Lte extends SemSelOperator {
    override def toString: String = "<="
  }
  private[this] case object Lt extends SemSelOperator {
    override def toString: String = "<"
  }
  private[this] case object Gte extends SemSelOperator {
    override def toString: String = ">="
  }
  private[this] case object Gt extends SemSelOperator {
    override def toString: String = ">"
  }
  private[this] case object Eq extends SemSelOperator {
    override def toString: String = "="
  }

  private[SemanticSelector] final case class SemSelAndChunk(comparators: Seq[SemComparator]) {
    def matches(version: VersionNumber): Boolean = {
      comparators.forall(_.matches(version))
    }
    override def toString: String = comparators.map(_.toString).mkString(" ")
  }
  private[SemanticSelector] object SemSelAndChunk {
    def apply(andClauseToken: String): SemSelAndChunk = parse(andClauseToken)
    private[this] def parse(andClauseToken: String): SemSelAndChunk = {
      val comparatorTokens = andClauseToken.split("\\s+")
      val hyphenIndex = comparatorTokens.indexWhere(_ == "-")
      val comparators = if (hyphenIndex == -1) {
        comparatorTokens.map(SemComparator.apply)
      } else {
        // interpret `A.B.C - D.E.F` to `>=A.B.C <=D.E.F`
        val (before, after) = comparatorTokens.splitAt(hyphenIndex)
        (before.lastOption, after.drop(1).headOption) match {
          case (Some(fromStr), Some(toStr)) =>
            // from and to can not have an operator.
            if (hasOperator(fromStr) || hasOperator(toStr)) {
              throw new IllegalArgumentException(
                s"Invalid ' - ' range, both side of comparators can not have an operator: $fromStr - $toStr")
            }
            val from = SemComparator(fromStr)
            val to = SemComparator(toStr)
            val comparatorsBefore = before.dropRight(1).map(SemComparator.apply)
            val comparatorsAfter = after.drop(2) match {
              case tokens if !tokens.isEmpty =>
                parse(tokens.mkString(" ")).comparators
              case _ => Seq.empty
            }
            from.copy(op = Gte) +:
              to.copy(op = Lte) +:
              (comparatorsBefore ++ comparatorsAfter)
          case _ =>
            throw new IllegalArgumentException(
              s"Invalid ' - ' range position, both side of versions must be specified: $andClauseToken")
        }
      }
      SemSelAndChunk(comparators.flatMap(_.expandWildcard))
    }

    private[this] def hasOperator(comparator: String): Boolean = {
      comparator.startsWith("<") ||
      comparator.startsWith(">") ||
      comparator.startsWith("=")
    }
  }

  private[SemanticSelector] final case class SemComparator private (
      op: SemSelOperator,
      major: Option[Long],
      minor: Option[Long],
      patch: Option[Long],
      tags: Seq[String]
  ) {
    def matches(version: VersionNumber): Boolean = {
      // Fill empty fields of version specifier with 0 or max value of Long.
      // By filling them, SemComparator realize the properties below
      // `<=1.0` is equivalent to `<1.1.0` (`<=1.0.${Long.MaxValue}`)
      // `<1.0` is equivalent to `<1.0.0`
      // `>=1.0` is equivalent to `>=1.0.0`
      // `>1.0` is equivalent to `>=1.1.0` (`>1.0.${Long.MaxValue}`)
      //
      // However this fills 0 for a comparator that have `=` operator,
      // a comparator that have empty part of version and `=` operator won't appear
      // because of expanding it to and clause of comparators.
      val assumed = op match {
        case Lte => Long.MaxValue
        case Lt  => 0L
        case Gte => 0L
        case Gt  => Long.MaxValue
        case Eq  => 0L
      }
      // empty fields of the version number are assumed to be 0.
      val versionNumber =
        (version._1.getOrElse(0L), version._2.getOrElse(0L), version._3.getOrElse(0L))
      val selector = (major.getOrElse(assumed), minor.getOrElse(assumed), patch.getOrElse(assumed))
      val normalVersionCmp =
        implicitly[Ordering[(Long, Long, Long)]].compare(versionNumber, selector)
      val cmp =
        if (normalVersionCmp == 0) SemComparator.comparePreReleaseTags(version.tags, tags)
        else normalVersionCmp
      op match {
        case Lte if cmp <= 0 => true
        case Lt if cmp < 0   => true
        case Gte if cmp >= 0 => true
        case Gt if cmp > 0   => true
        case Eq if cmp == 0  => true
        case _               => false
      }
    }

    // Expand wildcard with `=` operator to and clause of comparators.
    // `=1.0` is equivalent to `>=1.0 <=1.0`
    def expandWildcard: Seq[SemComparator] = {
      if (op == Eq && !allFieldsSpecified) {
        Seq(this.copy(op = Gte), this.copy(op = Lte))
      } else {
        Seq(this)
      }
    }
    private[this] def allFieldsSpecified: Boolean =
      major.isDefined && minor.isDefined && patch.isDefined

    override def toString: String = {
      val versionStr = Seq(major, minor, patch)
        .collect {
          case Some(v) => v.toString
        }
        .mkString(".")
      val tagsStr = if (tags.nonEmpty) s"-${tags.mkString("-")}" else ""
      s"$op$versionStr$tagsStr"
    }
  }
  private[SemanticSelector] object SemComparator {
    def apply(comparator: String): SemComparator = parse(comparator)
    private[this] val ComparatorRegex = """(?x)^
      ([<>]=?|=)?
      (?:(\d+|[xX*])
        (?:\.(\d+|[xX*])
          (?:\.(\d+|[xX*]))?
        )?
      )((?:-\w+)*)$
    """.r
    private[this] def parse(comparator: String): SemComparator = {
      comparator match {
        case ComparatorRegex(rawOp, rawMajor, rawMinor, rawPatch, ts) =>
          val opStr = Option(rawOp)
          val major = Option(rawMajor)
          val minor = Option(rawMinor)
          val patch = Option(rawPatch)
          val tags = splitDash(ts)

          // Trim wildcard(x, X, *) and re-parse it.
          // By trimming it, comparator realize the property like
          // `=1.2.x` is equivalent to `=1.2`.
          val hasXrangeSelector = Seq(major, minor, patch).exists {
            case Some(str) => str.matches("[xX*]")
            case None      => false
          }
          if (hasXrangeSelector) {
            if (tags.nonEmpty)
              throw new IllegalArgumentException(
                s"Pre-release version requires major, minor, patch versions to be specified: $comparator")
            val numbers = Seq(major, minor, patch).takeWhile {
              case Some(str) => str.matches("\\d+")
              case None      => false
            }
            parse(
              numbers
                .collect {
                  case Some(v) => v.toString
                }
                .mkString(".")
            )
          } else {
            if (tags.nonEmpty && (major.isEmpty || minor.isEmpty || patch.isEmpty))
              throw new IllegalArgumentException(
                s"Pre-release version requires major, minor, patch versions to be specified: $comparator")
            val operator = opStr match {
              case Some("<")  => Lt
              case Some("<=") => Lte
              case Some(">")  => Gt
              case Some(">=") => Gte
              case Some("=")  => Eq
              case None       => Eq
              case Some(_) =>
                throw new IllegalArgumentException(s"Invalid operator: $opStr")
            }
            SemComparator(
              operator,
              major.map(_.toLong),
              minor.map(_.toLong),
              patch.map(_.toLong),
              tags
            )
          }
        case _ => throw new IllegalArgumentException(s"Invalid comparator: $comparator")
      }
    }
    private[this] def splitOn[A](s: String, sep: Char): Vector[String] =
      if (s eq null) Vector()
      else s.split(sep).filterNot(_ == "").toVector
    private[this] def splitDash(s: String) = splitOn(s, '-')

    private[SemComparator] def comparePreReleaseTags(ts1: Seq[String], ts2: Seq[String]): Int = {
      // > When major, minor, and patch are equal, a pre-release version has lower precedence than a normal version.
      if (ts1.isEmpty && ts2.isEmpty) 0
      else if (ts1.nonEmpty && ts2.isEmpty) -1 // ts1 is pre-release version
      else if (ts1.isEmpty && ts2.nonEmpty) 1 // ts2 is pre-release version
      else compareTags(ts1, ts2)
    }

    @tailrec
    private[this] def compareTags(ts1: Seq[String], ts2: Seq[String]): Int = {
      // > A larger set of pre-release fields has a higher precedence than a smaller set,
      // > if all of the preceding identifiers are equal.
      if (ts1.isEmpty && ts2.isEmpty) 0
      else if (ts1.nonEmpty && ts2.isEmpty) 1
      else if (ts1.isEmpty && ts2.nonEmpty) -1
      else {
        val ts1head = ts1.head
        val ts2head = ts2.head
        val cmp = (ts1head.matches("\\d+"), ts2head.matches("\\d+")) match {
          // Identifiers consisting of only digits are compared numerically.
          // Numeric identifiers always have lower precedence than non-numeric identifiers.
          // Identifiers with letters are compared case insensitive lexical order.
          case (true, true)   => implicitly[Ordering[Long]].compare(ts1head.toLong, ts2head.toLong)
          case (false, true)  => 1
          case (true, false)  => -1
          case (false, false) => ts1head.toLowerCase.compareTo(ts2head.toLowerCase)
        }
        if (cmp == 0) compareTags(ts1.tail, ts2.tail)
        else cmp
      }
    }
  }
}
