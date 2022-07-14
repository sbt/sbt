/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import org.scalatest.flatspec.AnyFlatSpec

class CleanStringSpec extends AnyFlatSpec {
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
      Array[Byte](27, 91, 50, 75, 27, 91, 51, 65, 27, 91, 49, 48, 48, 48, 68)
    val (bytes, len) = EscHelpers.strip(original, stripAnsi = true, stripColor = true)
    assert(len == 0)
  }
  it should "remove moves in string with moves and letters" in {
    val original =
      Array[Byte](27, 91, 50, 75, 27, 91, 51, 65) ++ "foo".getBytes ++ Array[Byte](27, 91, 49, 48,
        48, 48, 68)
    val (bytes, len) = EscHelpers.strip(original, stripAnsi = true, stripColor = true)
    assert(new String(bytes, 0, len) == "foo")
  }
  it should "preserve colors" in {
    val original =
      Array[Byte](27, 91, 49, 48, 48, 48, 68, 27, 91, 48, 74, 102, 111, 111, 27, 91, 51, 54, 109,
        62, 32, 27, 91, 48, 109)
    // this is taken from an sbt prompt that looks like "foo> " with the > rendered blue
    val colorArrow = new String(Array[Byte](27, 91, 51, 54, 109, 62))
    val (bytes, len) = EscHelpers.strip(original, stripAnsi = true, stripColor = false)
    assert(new String(bytes, 0, len) == "foo" + colorArrow + " " + scala.Console.RESET)
  }
  it should "remove unusual escape characters" in {
    val original = new String(
      Array[Byte](27, 91, 63, 49, 108, 27, 62, 27, 91, 63, 49, 48, 48, 48, 108, 27, 91, 63, 50, 48,
        48, 52, 108)
    )
    assert(EscHelpers.stripColorsAndMoves(original).isEmpty)
  }
  it should "remove bracketed paste csi" in {
    // taken from a test project prompt
    val original =
      Array[Byte](27, 91, 63, 50, 48, 48, 52, 104, 115, 98, 116, 58, 114, 101, 112, 114, 111, 62,
        32)
    val (bytes, len) = EscHelpers.strip(original, stripAnsi = true, stripColor = false)
    assert(new String(bytes, 0, len) == "sbt:repro> ")
  }
  it should "strip colors" in {
    // taken from utest output
    val original =
      Array[Byte](91, 105, 110, 102, 111, 93, 32, 27, 91, 51, 50, 109, 43, 27, 91, 51, 57, 109, 32,
        99, 111, 109, 46, 97, 99, 109, 101, 46, 67, 111, 121, 111, 116, 101, 84, 101, 115, 116, 46,
        109, 97, 107, 101, 84, 114, 97, 112, 32, 27, 91, 50, 109, 57, 109, 115, 27, 91, 48, 109, 32,
        32, 27, 91, 48, 74, 10)
    val (bytes, len) = EscHelpers.strip(original, stripAnsi = false, stripColor = true)
    val expected = "[info] + com.acme.CoyoteTest.makeTrap 9ms  " +
      new String(Array[Byte](27, 91, 48, 74, 10))
    assert(new String(bytes, 0, len) == expected)

    val (bytes2, len2) = EscHelpers.strip(original, stripAnsi = true, stripColor = true)
    val expected2 = "[info] + com.acme.CoyoteTest.makeTrap 9ms  \n"
    assert(new String(bytes2, 0, len2) == expected2)
  }
}
