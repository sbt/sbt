/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

import scala.collection.JavaConverters._

private[sbt] sealed trait Prompt {
  def mkPrompt: () => String
  def render(): String
  def wrappedOutputStream(terminal: Terminal): OutputStream
}

private[sbt] object Prompt {
  private[sbt] case class AskUser(override val mkPrompt: () => String) extends Prompt {
    private[this] val bytes = new LinkedBlockingQueue[Int]
    override def wrappedOutputStream(terminal: Terminal): OutputStream = new OutputStream {
      override def write(b: Int): Unit = {
        if (b == 10) bytes.clear()
        else bytes.put(b)
        terminal.withPrintStream { p =>
          p.write(b)
          p.flush()
        }
      }
      override def flush(): Unit = terminal.withPrintStream(_.flush())
    }

    override def render(): String =
      EscHelpers.stripMoves(new String(bytes.asScala.toArray.map(_.toByte)))
  }
  private[sbt] trait NoPrompt extends Prompt {
    override val mkPrompt: () => String = () => ""
    override def render(): String = ""
    override def wrappedOutputStream(terminal: Terminal): OutputStream = terminal.outputStream
  }
  private[sbt] case object Running extends NoPrompt
  private[sbt] case object Batch extends NoPrompt
  private[sbt] case object Watch extends NoPrompt
}
