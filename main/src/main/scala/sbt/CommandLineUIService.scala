package sbt

import sbt.internal.util.{ JLine, SimpleReader }

trait CommandLineUIService extends InteractionService {
  override def readLine(prompt: String, mask: Boolean): Option[String] = {
    val maskChar = if (mask) Some('*') else None
    SimpleReader.readLine(prompt, maskChar)
  }
  // TODO - Implement this better.
  override def confirm(msg: String): Boolean = {
    object Assent {
      def unapply(in: String): Boolean = {
        (in == "y" || in == "yes")
      }
    }
    SimpleReader.readLine(msg + " (yes/no): ", None) match {
      case Some(Assent()) => true
      case _              => false
    }
  }

  override def terminalWidth: Int = JLine.usingTerminal(_.getWidth)

  override def terminalHeight: Int = JLine.usingTerminal(_.getHeight)
}

object CommandLineUIService extends CommandLineUIService
