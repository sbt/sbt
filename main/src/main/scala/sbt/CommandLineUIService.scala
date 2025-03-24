/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.{ SimpleReader, Terminal as ITerminal }

trait CommandLineUIService extends InteractionService {
  override def readLine(prompt: String, mask: Boolean): Option[String] = {
    val maskChar = if (mask) Some('*') else None
    SimpleReader(ITerminal.get).readLine(prompt, maskChar)
  }
  // TODO - Implement this better.
  override def confirm(msg: String): Boolean = {
    object Assent {
      def unapply(in: String): Boolean = {
        (in == "y" || in == "yes")
      }
    }
    SimpleReader(ITerminal.get).readLine(msg + " (yes/no): ", None) match {
      case Some(Assent()) => true
      case _              => false
    }
  }

  override def terminalWidth: Int = ITerminal.get.getWidth

  override def terminalHeight: Int = ITerminal.get.getHeight
}

object CommandLineUIService extends CommandLineUIService
