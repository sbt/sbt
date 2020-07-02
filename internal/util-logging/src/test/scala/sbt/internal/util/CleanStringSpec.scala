/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import org.scalatest.FlatSpec

class CleanStringSpec extends FlatSpec {
  "EscHelpers" should "not modify normal strings" in {
    val cleanString = s"1234"
    assert(EscHelpers.stripColorsAndMoves(cleanString) == cleanString)
  }
  it should "remove delete lines" in {
    val clean = "1234"
    val string = s"${ConsoleAppender.DeleteLine}$clean"
    assert(EscHelpers.stripColorsAndMoves(string) == clean)
  }
  it should "remove cursor left" in {
    val clean = "1234"
    val backspaced = s"1235${ConsoleAppender.cursorLeft(1)}${ConsoleAppender.clearLine(0)}4"
    assert(EscHelpers.stripColorsAndMoves(backspaced) == clean)
  }
  it should "remove colors" in {
    val clean = "1234"
    val colored = s"${scala.Console.RED}$clean${scala.Console.RESET}"
    assert(EscHelpers.stripColorsAndMoves(colored) == clean)
  }
  it should "remove backspaces" in {
    // Taken from an actual failure case. In the scala client, type 'clean', then type backspace
    // five times to clear 'clean' and then retype 'clean'.
    val bytes = Array[Byte](27, 91, 50, 75, 27, 91, 48, 74, 27, 91, 50, 75, 27, 91, 49, 48, 48, 48,
      68, 115, 98, 116, 58, 115, 99, 97, 108, 97, 45, 99, 111, 109, 112, 105, 108, 101, 27, 91, 51,
      54, 109, 62, 32, 27, 91, 48, 109, 99, 108, 101, 97, 110, 8, 27, 91, 75, 110)
    val str = new String(bytes)
    assert(EscHelpers.stripColorsAndMoves(str) == "sbt:scala-compile> clean")
  }
  it should "handle cursor left overwrite" in {
    val clean = "1234"
    val backspaced = s"1235${8.toChar}4${8.toChar}"
    assert(EscHelpers.stripColorsAndMoves(backspaced) == clean)
  }
  it should "remove moves in string with only moves" in {
    val original =
      new String(Array[Byte](27, 91, 50, 75, 27, 91, 51, 65, 27, 91, 49, 48, 48, 48, 68))
    assert(EscHelpers.stripMoves(original) == "")
  }
  it should "remove moves in string with moves and letters" in {
    val original = new String(
      Array[Byte](27, 91, 50, 75, 27, 91, 51, 65) ++ "foo".getBytes ++ Array[Byte](27, 91, 49, 48,
        48, 48, 68)
    )
    assert(EscHelpers.stripMoves(original) == "foo")
  }
  it should "preserve colors" in {
    val original = new String(
      Array[Byte](27, 91, 49, 48, 48, 48, 68, 27, 91, 48, 74, 102, 111, 111, 27, 91, 51, 54, 109,
        62, 32, 27, 91, 48, 109)
    ) // this is taken from an sbt prompt that looks like "foo> " with the > rendered blue
    val colorArrow = new String(Array[Byte](27, 91, 51, 54, 109, 62))
    assert(EscHelpers.stripMoves(original) == "foo" + colorArrow + " " + scala.Console.RESET)
  }
}
