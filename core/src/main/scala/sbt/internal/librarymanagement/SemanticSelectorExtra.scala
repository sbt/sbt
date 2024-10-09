package sbt.internal.librarymanagement

import sbt.librarymanagement.VersionNumber
import sbt.internal.librarymanagement.SemSelOperator.{ Lt, Lte, Gt, Gte, Eq }

import scala.annotation.tailrec
import java.util.Locale

private[librarymanagement] abstract class SemSelAndChunkFunctions {
  protected def parse(andClauseToken: String): SemSelAndChunk = {
    val comparatorTokens =
      scala.collection.immutable.ArraySeq.unsafeWrapArray(andClauseToken.split("\\s+"))
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
              s"Invalid ' - ' range, both side of comparators can not have an operator: $fromStr - $toStr"
            )
          }
          val from = SemComparator(fromStr)
          val to = SemComparator(toStr)
          val comparatorsBefore = before.dropRight(1).map(SemComparator.apply)
          val comparatorsAfter = after.drop(2) match {
            case tokens if !tokens.isEmpty =>
              parse(tokens.mkString(" ")).comparators
            case _ => Seq.empty
          }
          from.withOp(Gte) +: to.withOp(Lte) +:
            (comparatorsBefore ++ comparatorsAfter)
        case _ =>
          throw new IllegalArgumentException(
            s"Invalid ' - ' range position, both side of versions must be specified: $andClauseToken"
          )
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

private[librarymanagement] abstract class SemComparatorExtra {
  val op: SemSelOperator
  val major: Option[Long]
  val minor: Option[Long]
  val patch: Option[Long]
  val tags: Seq[String]

  protected def toStringImpl: String = {
    val versionStr = Seq(major, minor, patch)
      .collect { case Some(v) =>
        v.toString
      }
      .mkString(".")
    val tagsStr = if (tags.nonEmpty) s"-${tags.mkString("-")}" else ""
    s"$op$versionStr$tagsStr"
  }

  protected def matchesImpl(version: VersionNumber): Boolean = {
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
      if (normalVersionCmp == 0) comparePreReleaseTags(version.tags, tags)
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
  private[this] def comparePreReleaseTags(ts1: Seq[String], ts2: Seq[String]): Int = {
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
        case (true, true)  => implicitly[Ordering[Long]].compare(ts1head.toLong, ts2head.toLong)
        case (false, true) => 1
        case (true, false) => -1
        case (false, false) =>
          ts1head.toLowerCase(Locale.ENGLISH).compareTo(ts2head.toLowerCase(Locale.ENGLISH))
      }
      if (cmp == 0) compareTags(ts1.tail, ts2.tail)
      else cmp
    }
  }

  // Expand wildcard with `=` operator to and clause of comparators.
  // `=1.0` is equivalent to `>=1.0 <=1.0`
  protected def allFieldsSpecified: Boolean =
    major.isDefined && minor.isDefined && patch.isDefined
}

private[librarymanagement] abstract class SemComparatorFunctions {
  private[this] val ComparatorRegex = """(?x)^
      ([<>]=?|=)?
      (?:(\d+|[xX*])
        (?:\.(\d+|[xX*])
          (?:\.(\d+|[xX*]))?
        )?
      )((?:-\w+(?:\.\w+)*)*)$
    """.r
  protected def parse(comparator: String): SemComparator = {
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
              s"Pre-release version requires major, minor, patch versions to be specified: $comparator"
            )
          val numbers = Seq(major, minor, patch).takeWhile {
            case Some(str) => str.matches("\\d+")
            case None      => false
          }
          parse(
            numbers
              .collect { case Some(v) =>
                v.toString
              }
              .mkString(".")
          )
        } else {
          if (tags.nonEmpty && (major.isEmpty || minor.isEmpty || patch.isEmpty))
            throw new IllegalArgumentException(
              s"Pre-release version requires major, minor, patch versions to be specified: $comparator"
            )
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
}
