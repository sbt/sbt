/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.{ InputStream, PipedInputStream, PipedOutputStream }
import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.tailrec
import scala.collection.JavaConverters._

/**
 * Creates a copy of the provided [[InputStream]] that forwards its contents to an arbitrary
 * number of connected [[InputStream]] instances via pipe.
 * @param in the [[InputStream]] to wrap.
 */
private[internal] class DupedInputStream(val in: InputStream)
    extends InputStream
    with AutoCloseable {

  /**
   * Returns a copied [[InputStream]] that will receive the same bytes as System.in.
   * @return
   */
  def duped: InputStream = {
    val pipedOutputStream = new PipedOutputStream()
    pipes += pipedOutputStream
    val res = new PollingInputStream(new PipedInputStream(pipedOutputStream))
    buffer.forEach(pipedOutputStream.write(_))
    res
  }

  private[this] val pipes = new java.util.Vector[PipedOutputStream].asScala
  private[this] val buffer = new LinkedBlockingQueue[Int]
  private class PollingInputStream(val pipedInputStream: PipedInputStream) extends InputStream {
    override def available(): Int = {
      fillBuffer()
      pipedInputStream.available()
    }
    override def read(): Int = {
      fillBuffer()
      pipedInputStream.read
    }
  }
  override def available(): Int = {
    fillBuffer()
    buffer.size
  }
  override def read(): Int = {
    fillBuffer()
    buffer.take()
  }

  private[this] def fillBuffer(): Unit = synchronized {
    @tailrec
    def impl(): Unit = in.available match {
      case i if i > 0 =>
        val res = in.read()
        buffer.add(res)
        pipes.foreach { p =>
          p.write(res)
          p.flush()
        }
        impl()
      case _ =>
    }
    impl()
  }
}
