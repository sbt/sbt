/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object EscHelpers {

  /** Escape character, used to introduce an escape sequence. */
  final val ESC = '\u001B'

  /**
   * An escape terminator is a character in the range `@` (decimal value 64) to `~` (decimal value 126).
   * It is the final character in an escape sequence.
   *
   * cf. http://en.wikipedia.org/wiki/ANSI_escape_code#CSI_codes
   */
  private[sbt] def isEscapeTerminator(c: Char): Boolean =
    c >= '@' && c <= '~'

  /**
   * Test if the character AFTER an ESC is the ANSI CSI.
   *
   * see: http://en.wikipedia.org/wiki/ANSI_escape_code
   *
   * The CSI (control sequence instruction) codes start with ESC + '['.   This is for testing the second character.
   *
   * There is an additional CSI (one character) that we could test for, but is not frequnetly used, and we don't
   * check for it.
   *
   * cf. http://en.wikipedia.org/wiki/ANSI_escape_code#CSI_codes
   */
  private def isCSI(c: Char): Boolean = c == '['

  /**
   * Tests whether or not a character needs to immediately terminate the ANSI sequence.
   *
   * c.f. http://en.wikipedia.org/wiki/ANSI_escape_code#Sequence_elements
   */
  private def isAnsiTwoCharacterTerminator(c: Char): Boolean =
    (c >= '@') && (c <= '_')

  /**
   * Returns true if the string contains the ESC character.
   *
   * TODO - this should handle raw CSI (not used much)
   */
  def hasEscapeSequence(s: String): Boolean =
    s.indexOf(ESC) >= 0

  /**
   * Returns the string `s` with escape sequences removed.
   * An escape sequence starts with the ESC character (decimal value 27) and ends with an escape terminator.
   * @see isEscapeTerminator
   */
  def removeEscapeSequences(s: String): String =
    if (s.isEmpty || !hasEscapeSequence(s))
      s
    else {
      val sb = new java.lang.StringBuilder
      nextESC(s, 0, sb)
      sb.toString
    }

  private[this] def nextESC(s: String, start: Int, sb: java.lang.StringBuilder): Unit = {
    val escIndex = s.indexOf(ESC, start)
    if (escIndex < 0) {
      sb.append(s, start, s.length)
      ()
    } else {
      sb.append(s, start, escIndex)
      val next: Int =
        if (escIndex + 1 >= s.length) skipESC(s, escIndex + 1)
        // If it's a CSI we skip past it and then look for a terminator.
        else if (isCSI(s.charAt(escIndex + 1))) skipESC(s, escIndex + 2)
        else if (isAnsiTwoCharacterTerminator(s.charAt(escIndex + 1))) escIndex + 2
        else {
          // There could be non-ANSI character sequences we should make sure we handle here.
          skipESC(s, escIndex + 1)
        }
      nextESC(s, next, sb)
    }
  }
  private[this] val esc = 1
  private[this] val csi = 2
  def cursorPosition(s: String): Int = {
    val bytes = s.getBytes
    var i = 0
    var index = 0
    var state = 0
    val digit = new ArrayBuffer[Byte]
    var leftDigit = -1
    while (i < bytes.length) {
      bytes(i) match {
        case 27 => state = esc
        case b if (state == esc || state == csi) && b >= 48 && b < 58 =>
          state = csi
          digit += b
        case '[' if state == esc => state = csi
        case 8 =>
          state = 0
          index = index - 1
        case b if state == csi =>
          leftDigit = Try(new String(digit.toArray).toInt).getOrElse(0)
          state = 0
          b.toChar match {
            case 'D' => index = math.max(index - leftDigit, 0)
            case 'C' => index += leftDigit
            case 'K' =>
            case 'J' => if (leftDigit == 2) index = 0
            case 'm' =>
            case ';' => state = csi
            case _   =>
          }
          digit.clear()
        case _ =>
          index += 1
      }
      i += 1
    }
    index
  }
  def stripMoves(s: String): String = {
    val bytes = s.getBytes
    val res = Array.fill[Byte](bytes.length)(0)
    var index = 0
    var lastEscapeIndex = -1
    var state = 0
    def set(b: Byte) = {
      res(index) = b
      index += 1
    }
    bytes.foreach { b =>
      set(b)
      b match {
        case 27 =>
          state = esc
          lastEscapeIndex = math.max(0, index)
        case b if b == '[' && state == esc => state = csi
        case 'm'                           => state = 0
        case b if state == csi && (b < 48 || b >= 58) && b != ';' =>
          state = 0
          index = math.max(0, lastEscapeIndex - 1)
        case b =>
      }
    }
    new String(res, 0, index)
  }
  def stripColorsAndMoves(s: String): String = {
    val bytes = s.getBytes
    val res = Array.fill[Byte](bytes.length)(0)
    var i = 0
    var index = 0
    var state = 0
    var limit = 0
    val digit = new ArrayBuffer[Byte]
    var leftDigit = -1
    bytes.foreach {
      case 27 => state = esc
      case b if (state == esc || state == csi) && b >= 48 && b < 58 =>
        state = csi
        digit += b
      case '[' if state == esc => state = csi
      case 8 =>
        state = 0
        index = math.max(index - 1, 0)
      case b if state == csi =>
        leftDigit = Try(new String(digit.toArray).toInt).getOrElse(0)
        state = 0
        b.toChar match {
          case 'D' => index = math.max(index - leftDigit, 0)
          case 'C' => index = math.min(limit, math.min(index + leftDigit, res.length - 1))
          case 'K' | 'J' =>
            if (leftDigit > 0) (0 until index).foreach(res(_) = 32)
            else res(index) = 32
          case 'm' =>
          case ';' => state = csi
          case _   =>
        }
        digit.clear()
      case b =>
        res(index) = b
        index += 1
        limit = math.max(limit, index)
    }
    new String(res, 0, limit)
  }

  /** Skips the escape sequence starting at `i-1`.  `i` should be positioned at the character after the ESC that starts the sequence. */
  private[this] def skipESC(s: String, i: Int): Int = {
    if (i >= s.length) {
      i
    } else if (isEscapeTerminator(s.charAt(i))) {
      i + 1
    } else {
      skipESC(s, i + 1)
    }
  }

}
