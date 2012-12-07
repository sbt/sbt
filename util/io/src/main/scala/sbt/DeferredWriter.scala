package sbt

	import java.io.Writer

final class DeferredWriter(make: => Writer) extends Writer
{
	private[this] var opened = false
	private[this] var delegate0: Writer = _
	private[this] def delegate: Writer = synchronized {
		if(delegate0 eq null) {
			delegate0 = make
			opened = true
		}
		delegate0
	}
	override def close() = synchronized {
		if(opened) delegate0.close()
	}

	override def append(c: Char): Writer = delegate.append(c)
	override def append(csq: CharSequence): Writer = delegate.append(csq)
	override def append(csq: CharSequence, start: Int, end: Int): Writer = delegate.append(csq, start, end)
	override def flush() = delegate.flush()
	override def write(cbuf: Array[Char]) = delegate.write(cbuf)
	override def write(cbuf: Array[Char], off: Int, len: Int): Unit = delegate.write(cbuf, off, len)
	override def write(c: Int): Unit = delegate.write(c)
	override def write(s: String): Unit = delegate.write(s)
	override def write(s: String, off: Int, len: Int): Unit = delegate.write(s, off, len)
}