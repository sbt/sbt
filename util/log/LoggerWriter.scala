/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010 Mark Harrah
 */
package sbt

/** Provides a `java.io.Writer` interface to a `Logger`.  Content is line-buffered and logged at `level`.
* A line is delimited by `nl`, which is by default the platform line separator.*/
class LoggerWriter(delegate: Logger, level: Level.Value, nl: String) extends java.io.Writer
{
	def this(delegate: Logger, level: Level.Value) = this(delegate, level, System.getProperty("line.separator"))
	
	private[this] val buffer = new StringBuilder

	override def close() = flush()
	override def flush(): Unit =
		synchronized {
			if(buffer.length > 0)
			{
				log(buffer.toString)
				buffer.clear()
			}
		}
	override def write(content: Array[Char], offset: Int, length: Int): Unit =
		synchronized {
			buffer.appendAll(content, offset, length)
			process()
		}

	private[this] def process()
	{
		val i = buffer.indexOf(nl)
		if(i >= 0)
		{
			log(buffer.substring(0, i))
			buffer.delete(0, i + nl.length)
			process()
		}
	}
	private[this] def log(s: String): Unit  =  delegate.log(level, s)
}