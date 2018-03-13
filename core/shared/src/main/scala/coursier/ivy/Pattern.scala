package coursier.ivy

import coursier.util.Traverse.TraverseOps
import coursier.util.ValidationNel
import fastparse.all._

final case class PropertiesPattern(chunks: Seq[PropertiesPattern.ChunkOrProperty]) {

  def string: String = chunks.map(_.string).mkString

  import PropertiesPattern.ChunkOrProperty

  def substituteProperties(properties: Map[String, String]): Either[String, Pattern] = {

    val validation = chunks.validationNelTraverse[String, Seq[Pattern.Chunk]] {
      case ChunkOrProperty.Prop(name, alternativesOpt) =>
        properties.get(name) match {
          case Some(value) =>
            ValidationNel.success(Seq(Pattern.Chunk.Const(value)))
          case None =>
            alternativesOpt match {
              case Some(alt) =>
                ValidationNel.fromEither(
                  PropertiesPattern(alt)
                    .substituteProperties(properties)
                    .right
                    .map(_.chunks.toVector)
                )
              case None =>
                ValidationNel.failure(name)
            }
        }

      case ChunkOrProperty.Opt(l @ _*) =>
        ValidationNel.fromEither(
          PropertiesPattern(l)
            .substituteProperties(properties)
            .right
            .map(l => Seq(Pattern.Chunk.Opt(l.chunks: _*)))
        )

      case ChunkOrProperty.Var(name) =>
        ValidationNel.success(Seq(Pattern.Chunk.Var(name)))

      case ChunkOrProperty.Const(value) =>
        ValidationNel.success(Seq(Pattern.Chunk.Const(value)))

    }.map(c => Pattern(c.flatten))

    validation.either.left.map { notFoundProps =>
      s"Property(ies) not found: ${notFoundProps.mkString(", ")}"
    }
  }
}

final case class Pattern(chunks: Seq[Pattern.Chunk]) {

  def +:(chunk: Pattern.Chunk): Pattern =
    Pattern(chunk +: chunks)

  import Pattern.Chunk

  def string: String = chunks.map(_.string).mkString

  def substituteVariables(variables: Map[String, String]): Either[String, String] = {

    def helper(chunks: Seq[Chunk]): ValidationNel[String, Seq[Chunk.Const]] =
      chunks.validationNelTraverse[String, Seq[Chunk.Const]] {
        case Chunk.Var(name) =>
          variables.get(name) match {
            case Some(value) =>
              ValidationNel.success(Seq(Chunk.Const(value)))
            case None =>
              ValidationNel.failure(name)
          }
        case Chunk.Opt(l @ _*) =>
          val res = helper(l)
          if (res.isSuccess)
            res
          else
            ValidationNel.success(Seq())
        case c: Chunk.Const =>
          ValidationNel.success(Seq(c))
      }.map(_.flatten)

    val validation = helper(chunks)

    validation.either match {
      case Left(notFoundVariables) =>
        Left(s"Variables not found: ${notFoundVariables.mkString(", ")}")
      case Right(constants) =>
        val b = new StringBuilder
        constants.foreach(b ++= _.value)
        Right(b.result())
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

  private def parser: Parser[Seq[ChunkOrProperty]] = {

    val notIn = s"[]{}()$$".toSet
    val chars = P(CharsWhile(c => !notIn(c)).!)
    val noHyphenChars = P(CharsWhile(c => !notIn(c) && c != '-').!)

    val constant = P(chars).map(ChunkOrProperty.Const)

    lazy val property: Parser[ChunkOrProperty.Prop] =
      P(s"$${" ~ noHyphenChars ~ ("-" ~ chunks).? ~ "}")
        .map { case (name, altOpt) => ChunkOrProperty.Prop(name, altOpt) }

    lazy val variable: Parser[ChunkOrProperty.Var] = P("[" ~ chars ~ "]").map(ChunkOrProperty.Var)

    lazy val optional: Parser[ChunkOrProperty.Opt] = P("(" ~ chunks ~ ")")
      .map(l => ChunkOrProperty.Opt(l: _*))

    lazy val chunks: Parser[Seq[ChunkOrProperty]] = P((constant | property | variable | optional).rep)
      .map(_.toVector) // "Vector" is more readable than "ArrayBuffer"

    chunks
  }


  def parse(pattern: String): Either[String, PropertiesPattern] =
    parser.parse(pattern) match {
      case f: Parsed.Failure =>
        Left(f.msg)
      case Parsed.Success(v, _) =>
        Right(PropertiesPattern(v))
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
