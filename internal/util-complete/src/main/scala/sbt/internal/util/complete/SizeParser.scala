/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util.complete

import sbt.internal.util.complete.DefaultParsers._

private[sbt] object SizeParser {
  def apply(s: String): Option[Long] = Parser.parse(s, value).toOption
  private sealed trait SizeUnit
  private case object Bytes extends SizeUnit
  private case object KiloBytes extends SizeUnit
  private case object MegaBytes extends SizeUnit
  private case object GigaBytes extends SizeUnit
  private def parseDouble(s: String): Parser[Either[Double, Long]] =
    try Parser.success(Left(java.lang.Double.valueOf(s)))
    catch { case _: NumberFormatException => Parser.failure(s"Couldn't parse $s as double.") }
  private def parseLong(s: String): Parser[Either[Double, Long]] =
    try Parser.success(Right(java.lang.Long.valueOf(s)))
    catch { case _: NumberFormatException => Parser.failure(s"Couldn't parse $s as double.") }
  private[this] val digit = charClass(_.isDigit, "digit")
  private[this] val numberParser: Parser[Either[Double, Long]] =
    (digit.+ ~ ('.'.examples() ~> digit.+).?).flatMap {
      case (leading, Some(decimalPart)) =>
        parseDouble(s"${leading.mkString}.${decimalPart.mkString}")
      case (leading, _) => parseLong(leading.mkString)
    }
  private[this] val unitParser: Parser[SizeUnit] =
    token("b" | "B" | "g" | "G" | "k" | "K" | "m" | "M").map {
      case "b" | "B" => Bytes
      case "g" | "G" => GigaBytes
      case "k" | "K" => KiloBytes
      case "m" | "M" => MegaBytes
    }
  private[this] def multiply(left: Either[Double, Long], right: Long): Long = left match {
    case Left(d)  => (d * right).toLong
    case Right(l) => l * right
  }
  private[sbt] val value: Parser[Long] =
    ((numberParser <~ SpaceClass
      .examples(" ", "b", "B", "g", "G", "k", "K", "m", "M")
      .*) ~ unitParser.?)
      .map {
        case (number, unit) =>
          unit match {
            case None | Some(Bytes) => multiply(number, right = 1)
            case Some(KiloBytes)    => multiply(number, right = 1024)
            case Some(MegaBytes)    => multiply(number, right = 1024 * 1024)
            case Some(GigaBytes)    => multiply(number, right = 1024 * 1024 * 1024)
          }
      }
}
