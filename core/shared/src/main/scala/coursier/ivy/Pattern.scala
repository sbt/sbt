package coursier.ivy

import scala.language.implicitConversions

import scalaz._, Scalaz._

import fastparse.all._

final case class PropertiesPattern(chunks: Seq[PropertiesPattern.ChunkOrProperty]) {

  def string: String = chunks.map(_.string).mkString

  import PropertiesPattern.ChunkOrProperty

  def substituteProperties(properties: Map[String, String]): String \/ Pattern = {

    val validation = chunks.toVector.traverseM[({ type L[X] = ValidationNel[String, X] })#L, Pattern.Chunk] {
      case ChunkOrProperty.Prop(name, alternativesOpt) =>
        properties.get(name) match {
          case Some(value) =>
            Vector(Pattern.Chunk.Const(value)).successNel
          case None =>
            alternativesOpt match {
              case Some(alt) =>
                PropertiesPattern(alt)
                  .substituteProperties(properties)
                  .map(_.chunks.toVector)
                  .validation
                  .toValidationNel
              case None =>
                name.failureNel
            }
        }

      case ChunkOrProperty.Opt(l @ _*) =>
        PropertiesPattern(l)
          .substituteProperties(properties)
          .map(l => Vector(Pattern.Chunk.Opt(l.chunks: _*)))
          .validation
          .toValidationNel

      case ChunkOrProperty.Var(name) =>
        Vector(Pattern.Chunk.Var(name)).successNel

      case ChunkOrProperty.Const(value) =>
        Vector(Pattern.Chunk.Const(value)).successNel

    }.map(Pattern(_))

    validation.disjunction.leftMap { notFoundProps =>
      s"Property(ies) not found: ${notFoundProps.toList.mkString(", ")}"
    }
  }
}

final case class Pattern(chunks: Seq[Pattern.Chunk]) {

  def +:(chunk: Pattern.Chunk): Pattern =
    Pattern(chunk +: chunks)

  import Pattern.Chunk

  def string: String = chunks.map(_.string).mkString

  def substituteVariables(variables: Map[String, String]): String \/ String = {

    def helper(chunks: Seq[Chunk]): ValidationNel[String, Seq[Chunk.Const]] =
      chunks.toVector.traverseU[ValidationNel[String, Seq[Chunk.Const]]] {
        case Chunk.Var(name) =>
          variables.get(name) match {
            case Some(value) =>
              Seq(Chunk.Const(value)).successNel
            case None =>
              name.failureNel
          }
        case Chunk.Opt(l @ _*) =>
          val res = helper(l)
          if (res.isSuccess)
            res
          else
            Seq().successNel
        case c: Chunk.Const =>
          Seq(c).successNel
      }.map(_.flatten)

    val validation = helper(chunks)

    validation match {
      case Failure(notFoundVariables) =>
        s"Variables not found: ${notFoundVariables.toList.mkString(", ")}".left
      case Success(constants) =>
        val b = new StringBuilder
        constants.foreach(b ++= _.value)
        b.result().right
    }
  }
}

object PropertiesPattern {

  sealed abstract class ChunkOrProperty extends Product with Serializable {
    def string: String
  }

  object ChunkOrProperty {
    final case class Prop(name: String, alternative: Option[Seq[ChunkOrProperty]]) extends ChunkOrProperty {
      def string: String =
      s"$${" + name + alternative.fold("")(alt => "-" + alt.map(_.string).mkString) + "}"
    }
    final case class Var(name: String) extends ChunkOrProperty {
      def string: String = "[" + name + "]"
    }
    final case class Opt(content: ChunkOrProperty*) extends ChunkOrProperty {
      def string: String = "(" + content.map(_.string).mkString + ")"
    }
    final case class Const(value: String) extends ChunkOrProperty {
      def string: String = value
    }

    implicit def fromString(s: String): ChunkOrProperty = Const(s)
  }

  private object Parser {

    private val notIn = s"[]{}()$$".toSet
    private val chars = P(CharsWhile(c => !notIn(c)).!)
    private val noHyphenChars = P(CharsWhile(c => !notIn(c) && c != '-').!)

    private val constant = P(chars).map(ChunkOrProperty.Const)

    private lazy val property: Parser[ChunkOrProperty.Prop] =
      P(s"$${" ~ noHyphenChars ~ ("-" ~ chunks).? ~ "}")
        .map { case (name, altOpt) => ChunkOrProperty.Prop(name, altOpt) }

    private lazy val variable: Parser[ChunkOrProperty.Var] = P("[" ~ chars ~ "]").map(ChunkOrProperty.Var)

    private lazy val optional: Parser[ChunkOrProperty.Opt] = P("(" ~ chunks ~ ")")
      .map(l => ChunkOrProperty.Opt(l: _*))

    lazy val chunks: Parser[Seq[ChunkOrProperty]] = P((constant | property | variable | optional).rep)
      .map(_.toVector) // "Vector" is more readable than "ArrayBuffer"
  }

  def parser: Parser[Seq[ChunkOrProperty]] = Parser.chunks


  def parse(pattern: String): String \/ PropertiesPattern =
    parser.parse(pattern) match {
      case f: Parsed.Failure =>
        f.msg.left
      case Parsed.Success(v, _) =>
        PropertiesPattern(v).right
    }

}

object Pattern {

  sealed abstract class Chunk extends Product with Serializable {
    def string: String
  }

  object Chunk {
    final case class Var(name: String) extends Chunk {
      def string: String = "[" + name + "]"
    }
    final case class Opt(content: Chunk*) extends Chunk {
      def string: String = "(" + content.map(_.string).mkString + ")"
    }
    final case class Const(value: String) extends Chunk {
      def string: String = value
    }

    implicit def fromString(s: String): Chunk = Const(s)
  }

  import Chunk.{ Var, Opt }

  // Corresponds to
  //   [organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

  val default = Pattern(
    Seq(
      Var("organisation"), "/",
      Var("module"), "/",
      Opt("scala_", Var("scalaVersion"), "/"),
      Opt("sbt_", Var("sbtVersion"), "/"),
      Var("revision"), "/",
      Var("type"), "s/",
      Var("artifact"), Opt("-", Var("classifier")), ".", Var("ext")
    )
  )

}
