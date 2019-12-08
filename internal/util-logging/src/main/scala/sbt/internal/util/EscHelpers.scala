package sbt.internal.util

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
