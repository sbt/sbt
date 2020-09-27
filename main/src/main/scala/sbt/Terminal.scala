/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ InputStream, PrintStream }
import sbt.internal.util.{ JLine3, Terminal => ITerminal }

/**
 * A Terminal represents a ui connection to sbt. It may control the embedded console
 * for an sbt server or it may control a remote client connected through sbtn. The
 * Terminal is particularly useful whenever an sbt task needs to receive input from
 * the user.
 *
 */
trait Terminal {

  /**
   * Returns the width of the terminal.
   *
   * @return the width ot the terminal
   */
  def getWidth: Int

  /**
   * Returns the height of the terminal
   *
   * @return the height of the terminal
   */
  def getHeight: Int

  /**
   * An input stream associated with the terminal. Bytes inputted by the
   * user may be read from this input stream.
   *
   * @return the terminal's input stream
   */
  def inputStream: InputStream

  /**
   * A print stream associated with the terminal. Writing to this output
   * stream should display text on the terminal.
   *
   * @return the terminal's input stream
   */
  def printStream: PrintStream

  /**
   * Sets the mode of the terminal. By default,the terminal will be in canonical mode
   * with echo enabled. This means that the terminal's inputStream will not return any
   * bytes until a newline is received and that all of the characters inputed by the
   * user will be echoed to the terminal's output stream.
   *
   * @param canonical toggles whether or not the terminal input stream is line buffered
   * @param echo toggles whether or not to echo the characters received from the terminal input stream
   */
  def setMode(canonical: Boolean, echo: Boolean): Unit

}
private[sbt] object Terminal {
  private[sbt] def apply(term: ITerminal): Terminal = new Terminal {
    override def getHeight: Int = term.getHeight
    override def getWidth: Int = term.getWidth
    override def inputStream: InputStream = term.inputStream
    override def printStream: PrintStream = term.printStream
    override def setMode(canonical: Boolean, echo: Boolean): Unit =
      JLine3.setMode(term, canonical, echo)
  }
}
