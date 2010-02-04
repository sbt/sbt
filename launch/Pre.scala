package xsbt.boot

object Pre
{
	def trimLeading(line: String) =
	{
		def newStart(i: Int): Int = if(i >= line.length || !Character.isWhitespace(line.charAt(i))) i else newStart(i+1)
		line.substring(newStart(0))
	}
	def isEmpty(line: String) = line.length == 0
	def isNonEmpty(line: String) = line.length > 0
	def assert(condition: Boolean, msg: => String): Unit = if (!condition) throw new AssertionError(msg)
	def assert(condition: Boolean): Unit = assert(condition, "Assertion failed")
	def require(condition: Boolean, msg: => String): Unit = if (!condition) throw new IllegalArgumentException(msg)
	def error(msg: String): Nothing = throw new BootException(prefixError(msg))
	def declined(msg: String): Nothing = throw new BootException(msg)
	def prefixError(msg: String): String = "Error during sbt execution: " + msg
	def toBoolean(s: String) = java.lang.Boolean.parseBoolean(s)
	def toArray[T](list: List[T]) =
	{
		val arr = new Array[T](list.length)
		def copy(i: Int, rem: List[T]): Unit =
			if(i < arr.length)
			{
				arr(i) = rem.head
				copy(i+1, rem.tail)
			}
		copy(0, list)
		arr
	}
	/* These exist in order to avoid bringing in dependencies on RichInt and ArrayBuffer, among others. */
	import java.io.File
	def concat(a: Array[File], b: Array[File]): Array[File] =
	{
		val n = new Array[File](a.length + b.length)
		java.lang.System.arraycopy(a, 0, n, 0, a.length)
		java.lang.System.arraycopy(b, 0, n, a.length, b.length)
		n
	}
	def array(files: File*): Array[File] = toArray(files.toList)
}
