/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

import DefaultParsers.*
import TypeString.*

/**
 * Basic representation of types parsed from Manifest.toString. This can only represent the
 * structure of parameterized types. All other types are represented by a TypeString with an empty
 * `args`.
 */
private[sbt] final class TypeString(val base: String, val args: List[TypeString]) {
  override def toString =
    if (base.startsWith(FunctionName))
      args.dropRight(1).mkString("(", ",", ")") + " => " + args.last
    else if (base.startsWith(TupleName))
      args.mkString("(", ",", ")")
    else
      cleanupTypeName(base) + (if (args.isEmpty) "" else args.mkString("[", ",", "]"))
}

private[sbt] object TypeString {

  /** Makes the string representation of a type as returned by Manifest.toString more readable. */
  def cleanup(typeString: String): String =
    parse(typeString, typeStringParser) match {
      case Right(ts) => ts.toString
      case Left(_)   => typeString
    }

  /**
   * Makes a fully qualified type name provided by Manifest.toString more readable. The argument
   * should be just a name (like scala.Tuple2) and not a full type (like scala.Tuple2[Int,Boolean])
   */
  def cleanupTypeName(base: String): String =
    dropPrefix(base).replace('$', '.')

  /**
   * Removes prefixes from a fully qualified type name that are unnecessary in the presence of
   * standard imports for an sbt setting. This does not use the compiler and is therefore a
   * conservative approximation.
   */
  def dropPrefix(base: String): String =
    if (base.startsWith(SbtPrefix)) base.substring(SbtPrefix.length)
    else if (base.startsWith(CollectionPrefix)) {
      val simple = base.substring(CollectionPrefix.length)
      if (ShortenCollection(simple)) simple else base
    } else if (base.startsWith(ScalaPrefix))
      base.substring(ScalaPrefix.length)
    else if (base.startsWith(JavaPrefix))
      base.substring(JavaPrefix.length)
    else
      TypeMap.getOrElse(base, base)

  final val CollectionPrefix = "scala.collection."
  final val FunctionName = "scala.Function"
  final val TupleName = "scala.Tuple"
  final val SbtPrefix = "sbt."
  final val ScalaPrefix = "scala."
  final val JavaPrefix = "java.lang."
  /* scala.collection.X -> X */
  val ShortenCollection = Set("Seq", "List", "Set", "Map", "Iterable")

  val TypeMap = Map(
    "java.io.File" -> "File",
    "java.net.URL" -> "URL",
    "java.net.URI" -> "URI"
  )

  /**
   * A Parser that extracts basic structure from the string representation of a type from
   * Manifest.toString. This is rudimentary and essentially only decomposes the string into names
   * and arguments for parameterized types.
   */
  lazy val typeStringParser: Parser[TypeString] = {
    def isFullScalaIDChar(c: Char) = isScalaIDChar(c) || c == '.' || c == '$'
    lazy val fullScalaID =
      identifier(IDStart, charClass(isFullScalaIDChar, "Scala identifier character"))
    lazy val tpe: Parser[TypeString] =
      for (id <- fullScalaID; args <- ('[' ~> rep1sep(tpe, ',') <~ ']').?)
        yield new TypeString(id, args.toList.flatten)
    tpe
  }
}
