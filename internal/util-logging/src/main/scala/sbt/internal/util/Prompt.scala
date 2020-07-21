/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.util.concurrent.LinkedBlockingQueue
import scala.collection.JavaConverters._

private[sbt] sealed trait Prompt {
  def mkPrompt: () => String
  def render(): String
  def reset(): Unit
}

private[sbt] object Prompt {
  private[sbt] case class AskUser(override val mkPrompt: () => String) extends Prompt {
    private[this] val bytes = new LinkedBlockingQueue[Byte]
    def write(b: Array[Byte]): Unit = b.foreach(bytes.put)
    override def render(): String = {
      val res = new String(bytes.asScala.toArray, "UTF-8")
      if (res.endsWith(System.lineSeparator)) "" else res
    }
    override def reset(): Unit = bytes.clear()
  }
  private[sbt] trait NoPrompt extends Prompt {
    override val mkPrompt: () => String = () => ""
    override def render(): String = ""
    override def reset(): Unit = {}
  }
  private[sbt] case object Running extends NoPrompt
  private[sbt] case object Batch extends NoPrompt
  private[sbt] case object Watch extends NoPrompt
  private[sbt] case object NoPrompt extends NoPrompt
}
