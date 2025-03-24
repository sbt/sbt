/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.complete

import sbt.internal.util.complete.DefaultParsers.*

private[sbt] object SizeParser {
  def apply(s: String): Option[Long] = Parser.parse(s, value).toOption
  private enum SizeUnit {
    case Bytes, KiloBytes, MegaBytes, GigaBytes
  }
  private def parseDouble(s: String): Parser[Either[Double, Long]] =
    s.toDoubleOption match {
      case Some(x) => Parser.success(Left(x))
      case _       => Parser.failure(s"Couldn't parse $s as double.")
    }
  private def parseLong(s: String): Parser[Either[Double, Long]] =
    s.toLongOption match {
      case Some(x) => Parser.success(Right(x))
      case _       => Parser.failure(s"Couldn't parse $s as double.")
    }
  private val digit = charClass(_.isDigit, "digit")
  private val numberParser: Parser[Either[Double, Long]] =
    (digit.+ ~ ('.'.examples() ~> digit.+).?).flatMap {
      case (leading, Some(decimalPart)) =>
        parseDouble(s"${leading.mkString}.${decimalPart.mkString}")
      case (leading, _) => parseLong(leading.mkString)
    }
  private val unitParser: Parser[SizeUnit] =
    token("b" | "B" | "g" | "G" | "k" | "K" | "m" | "M").map {
      case "b" | "B" => SizeUnit.Bytes
      case "g" | "G" => SizeUnit.GigaBytes
      case "k" | "K" => SizeUnit.KiloBytes
      case "m" | "M" => SizeUnit.MegaBytes
    }
  private def multiply(left: Either[Double, Long], right: Long): Long = left match {
    case Left(d)  => (d * right).toLong
    case Right(l) => l * right
  }
  private[sbt] val value: Parser[Long] =
    ((numberParser <~ SpaceClass
      .examples(" ", "b", "B", "g", "G", "k", "K", "m", "M")
      .*) ~ unitParser.?)
      .map { (number, unit) =>
        unit match {
          case None | Some(SizeUnit.Bytes) => multiply(number, right = 1L)
          case Some(SizeUnit.KiloBytes)    => multiply(number, right = 1024L)
          case Some(SizeUnit.MegaBytes)    => multiply(number, right = 1024L * 1024)
          case Some(SizeUnit.GigaBytes)    => multiply(number, right = 1024L * 1024 * 1024)
        }
      }
}
