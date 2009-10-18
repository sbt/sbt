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
	def error(msg: String): Nothing = throw new BootException(msg)
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
}
