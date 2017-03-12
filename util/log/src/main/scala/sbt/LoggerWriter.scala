/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

/**
  * Provides a `java.io.Writer` interface to a `Logger`.  Content is line-buffered and logged at `level`.
  * A line is delimited by `nl`, which is by default the platform line separator.
  */
class LoggerWriter(delegate: Logger,
                   unbufferedLevel: Option[Level.Value],
                   nl: String = System.getProperty("line.separator"))
    extends java.io.Writer {
  def this(delegate: Logger, level: Level.Value) = this(delegate, Some(level))
  def this(delegate: Logger) = this(delegate, None)

  private[this] val buffer = new StringBuilder
  private[this] val lines = new collection.mutable.ListBuffer[String]

  override def close() = flush()
  override def flush(): Unit =
    synchronized {
      if (buffer.nonEmpty) {
        log(buffer.toString)
        buffer.clear()
      }
    }
  def flushLines(level: Level.Value): Unit =
    synchronized {
      for (line <- lines)
        delegate.log(level, line)
      lines.clear()
    }
  override def write(content: Array[Char], offset: Int, length: Int): Unit =
    synchronized {
      buffer.appendAll(content, offset, length)
      process()
    }

  private[this] def process(): Unit = {
    val i = buffer.indexOf(nl)
    if (i >= 0) {
      log(buffer.substring(0, i))
      buffer.delete(0, i + nl.length)
      process()
    }
  }
  private[this] def log(s: String): Unit = unbufferedLevel match {
    case None        => lines += s
    case Some(level) => delegate.log(level, s)
  }
}
