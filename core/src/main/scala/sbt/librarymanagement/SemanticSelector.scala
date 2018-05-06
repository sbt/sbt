package sbt.librarymanagement

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
      patch: Option[Long]
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
      val cmp = implicitly[Ordering[(Long, Long, Long)]].compare(versionNumber, selector)
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
      s"$op$versionStr"
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
      )$
    """.r
    private[this] def parse(comparator: String): SemComparator = {
      comparator match {
        case ComparatorRegex(rawOp, rawMajor, rawMinor, rawPatch) =>
          val opStr = Option(rawOp)
          val major = Option(rawMajor)
          val minor = Option(rawMinor)
          val patch = Option(rawPatch)

          // Trim wildcard(x, X, *) and re-parse it.
          // By trimming it, comparator realize the property like
          // `=1.2.x` is equivalent to `=1.2`.
          val hasXrangeSelector = Seq(major, minor, patch).exists {
            case Some(str) => str.matches("[xX*]")
            case None      => false
          }
          if (hasXrangeSelector) {
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
              patch.map(_.toLong)
            )
          }
        case _ => throw new IllegalArgumentException(s"Invalid comparator: $comparator")
      }
    }
  }
}
