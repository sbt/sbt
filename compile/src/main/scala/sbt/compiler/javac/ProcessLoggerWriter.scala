package sbt.compiler.javac

import sbt.{ Level, ProcessLogger }

/** Delegates a stream into a process logger. Mimics LoggerWriter, but for the ProcessLogger interface which differs. */
private class ProcessLoggerWriter(delegate: ProcessLogger, level: Level.Value, nl: String = System.getProperty("line.separator")) extends java.io.Writer {
  private[this] val buffer = new StringBuilder
  override def close() = flush()
  override def flush(): Unit =
    synchronized {
      if (buffer.nonEmpty) {
        log(buffer.toString)
        buffer.clear()
      }
    }
  override def write(content: Array[Char], offset: Int, length: Int): Unit =
    synchronized {
      buffer.appendAll(content, offset, length)
      process()
    }

  private[this] def process() {
    val i = buffer.indexOf(nl)
    if (i >= 0) {
      log(buffer.substring(0, i))
      buffer.delete(0, i + nl.length)
      process()
    }
  }
  private[this] def log(s: String): Unit = level match {
    case Level.Warn | Level.Error => delegate.error(s)
    case Level.Info               => delegate.info(s)
  }
}
