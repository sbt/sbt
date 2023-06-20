/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

/**
 * InteractionService provides an abstration over standard input.
 * In the future this could be used to ask for inputs from
 * other forms of sbt clients such as thin clients and IDEs.
 */
abstract class InteractionService {

  /** Prompts the user for input, optionally with a mask for characters. */
  def readLine(prompt: String, mask: Boolean): Option[String]

  /** Ask the user to confirm something (yes or no) before continuing. */
  def confirm(msg: String): Boolean

  def terminalWidth: Int

  def terminalHeight: Int

  // TODO - Ask for input with autocomplete?
}
