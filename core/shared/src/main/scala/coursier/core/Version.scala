package coursier.core

import scala.annotation.tailrec
import coursier.core.compatibility._

/**
 *  Used internally by Resolver.
 *
 *  Same kind of ordering as aether-util/src/main/java/org/eclipse/aether/util/version/GenericVersion.java
 */
final case class Version(repr: String) extends Ordered[Version] {
  lazy val items = Version.items(repr)
  lazy val rawItems: Seq[Version.Item] = {
    val (first, tokens) = Version.Tokenizer(repr)
    first +: tokens.toVector.map { case (_, item) => item }
  }
  def compare(other: Version) = Version.listCompare(items, other.items)
  def isEmpty = items.forall(_.isEmpty)
}

object Version {

  sealed abstract class Item extends Ordered[Item] {
    def compare(other: Item): Int =
      (this, other) match {
        case (Number(a), Number(b)) => a.compare(b)
        case (BigNumber(a), BigNumber(b)) => a.compare(b)
        case (Number(a), BigNumber(b)) => -b.compare(a)
        case (BigNumber(a), Number(b)) => a.compare(b)
        case (Qualifier(_, a), Qualifier(_, b)) => a.compare(b)
        case (Literal(a), Literal(b)) => a.compareToIgnoreCase(b)
        case (BuildMetadata(_), BuildMetadata(_)) =>
          // Semver § 10: two versions that differ only in the build metadata, have the same precedence.
          // Might introduce some non-determinism though :-/
          0

        case _ =>
          val rel0 = compareToEmpty
          val rel1 = other.compareToEmpty

          if (rel0 == rel1) order.compare(other.order)
          else rel0.compare(rel1)
      }

    def order: Int
    def isEmpty: Boolean = compareToEmpty == 0
    def compareToEmpty: Int = 1
  }

  sealed abstract class Numeric extends Item {
    def repr: String
    def next: Numeric
  }
  final case class Number(value: Int) extends Numeric {
    val order = 0
    def next: Number = Number(value + 1)
    def repr: String = value.toString
    override def compareToEmpty = value.compare(0)
  }
  final case class BigNumber(value: BigInt) extends Numeric {
    val order = 0
    def next: BigNumber = BigNumber(value + 1)
    def repr: String = value.toString
    override def compareToEmpty = value.compare(0)
  }
  final case class Qualifier(value: String, level: Int) extends Item {
    val order = -2
    override def compareToEmpty = level.compare(0)
  }
  final case class Literal(value: String) extends Item {
    val order = -1
    override def compareToEmpty = if (value.isEmpty) 0 else 1
  }
  final case class BuildMetadata(value: String) extends Item {
    val order = 1
    override def compareToEmpty = if (value.isEmpty) 0 else 1
  }

  case object Min extends Item {
    val order = -8
    override def compareToEmpty = -1
  }
  case object Max extends Item {
    val order = 8
  }

  val empty = Number(0)

  val qualifiers = Seq[Qualifier](
    Qualifier("alpha", -5),
    Qualifier("beta", -4),
    Qualifier("milestone", -3),
    Qualifier("cr", -2),
    Qualifier("rc", -2),
    Qualifier("snapshot", -1),
    Qualifier("ga", 0),
    Qualifier("final", 0),
    Qualifier("sp", 1)
  )

  val qualifiersMap = qualifiers.map(q => q.value -> q).toMap

  object Tokenizer {
    sealed abstract class Separator
    case object Dot extends Separator
    case object Hyphen extends Separator
    case object Underscore extends Separator
    case object Plus extends Separator
    case object None extends Separator

    def apply(s: String): (Item, Stream[(Separator, Item)]) = {
      def parseItem(s: Stream[Char]): (Item, Stream[Char]) = {
        if (s.isEmpty || !s.head.letterOrDigit) (empty, s)
        else if (s.head.isDigit) {
          def digits(b: StringBuilder, s: Stream[Char]): (String, Stream[Char]) =
            if (s.isEmpty || !s.head.isDigit) (b.result(), s)
            else digits(b + s.head, s.tail)

          val (digits0, rem) = digits(new StringBuilder, s)
          val item =
            if (digits0.length >= 10) BigNumber(BigInt(digits0))
            else Number(digits0.toInt)

          (item, rem)
        } else {
          assert(s.head.letter)

          def letters(b: StringBuilder, s: Stream[Char]): (String, Stream[Char]) =
            if (s.isEmpty || !s.head.letter) (b.result().toLowerCase, s)
            else letters(b + s.head, s.tail)

          val (letters0, rem) = letters(new StringBuilder, s)
          val item =
            qualifiersMap.getOrElse(letters0, Literal(letters0))

          (item, rem)
        }
      }

      def parseSeparator(s: Stream[Char]): (Separator, Stream[Char]) = {
        assert(s.nonEmpty)

        s.head match {
          case '.' => (Dot, s.tail)
          case '-' => (Hyphen, s.tail)
          case '_' => (Underscore, s.tail)
          case '+' => (Plus, s.tail)
          case _ => (None, s)
        }
      }

      def helper(s: Stream[Char]): Stream[(Separator, Item)] = {
        if (s.isEmpty) Stream()
        else {
          val (sep, rem0) = parseSeparator(s)
          sep match {
            case Plus =>
              Stream((sep, BuildMetadata(rem0.mkString)))
            case _ =>
              val (item, rem) = parseItem(rem0)
              (sep, item) #:: helper(rem)
          }
        }
      }

      val (first, rem) = parseItem(s.toStream)
      (first, helper(rem))
    }
  }

  def postProcess(prevIsNumeric: Option[Boolean], item: Item, tokens0: Stream[(Tokenizer.Separator, Item)]): Stream[Item] = {
    val tokens = {
      var _tokens = tokens0

      if (isNumeric(item)) {
        val nextNonDotZero = _tokens.dropWhile{case (Tokenizer.Dot, n: Numeric) => n.isEmpty; case _ => false }
        if (nextNonDotZero.forall(t => t._1 == Tokenizer.Hyphen || ((t._1 == Tokenizer.Dot || t._1 == Tokenizer.None) && !isNumeric(t._2)))) { // Dot && isNumeric(t._2)
          _tokens = nextNonDotZero
        }
      }

      _tokens
    }

    def ifFollowedByNumberElse(ifFollowedByNumber: Item, default: Item) = {
      val followedByNumber = tokens.headOption
        .exists{ case (Tokenizer.None, num: Numeric) if !num.isEmpty => true; case _ => false }

      if (followedByNumber) ifFollowedByNumber
      else default
    }

    def next =
      if (tokens.isEmpty) Stream()
      else postProcess(Some(isNumeric(item)), tokens.head._2, tokens.tail)

    item match {
      case Literal("min") => Min #:: next
      case Literal("max") => Max #:: next
      case Literal("a") =>
        ifFollowedByNumberElse(qualifiersMap("alpha"), item) #:: next
      case Literal("b") =>
        ifFollowedByNumberElse(qualifiersMap("beta"), item) #:: next
      case Literal("m") =>
        ifFollowedByNumberElse(qualifiersMap("milestone"), item) #:: next
      case _ =>
        item #:: next
    }
  }

  def isNumeric(item: Item) = item match { case _: Numeric => true; case _ => false }

  def items(repr: String): List[Item] = {
    val (first, tokens) = Tokenizer(repr)

    postProcess(None, first, tokens).toList
  }

  @tailrec
  def listCompare(first: List[Item], second: List[Item]): Int = {
    if (first.isEmpty && second.isEmpty) 0
    else if (first.isEmpty) {
      assert(second.nonEmpty)
      -second.dropWhile(_.isEmpty).headOption.fold(0)(_.compareToEmpty)
    } else if (second.isEmpty) {
      assert(first.nonEmpty)
      first.dropWhile(_.isEmpty).headOption.fold(0)(_.compareToEmpty)
    } else {
      val rel = first.head.compare(second.head)
      if (rel == 0) listCompare(first.tail, second.tail)
      else rel
    }
  }

}
