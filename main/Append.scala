package sbt

	import java.io.File
	import Keys.Classpath
	import scala.annotation.implicitNotFound

object Append
{	
	@implicitNotFound(msg = "No implicit for Append.Value[${A}, ${B}] found,\n  so ${B} cannot be appended to ${A}")
	sealed trait Value[A,B]
	{
		def appendValue(a: A, b: B): A
	}
	@implicitNotFound(msg = "No implicit for Append.Values[${A}, ${B}] found,\n  so ${B} cannot be appended to ${A}")
	sealed trait Values[A,-B]
	{
		def appendValues(a: A, b: B): A
	}
	sealed trait Sequence[A,-B,T] extends Value[A,T] with Values[A,B]

	implicit def appendSeq[T, V <: T]: Sequence[Seq[T], Seq[V], V] = new Sequence[Seq[T], Seq[V], V] {
		def appendValues(a: Seq[T], b: Seq[V]): Seq[T] = a ++ b
		def appendValue(a: Seq[T], b: V): Seq[T] = a :+ b
	}
	implicit def appendString: Value[String, String] = new Value[String, String] {
		def appendValue(a: String, b: String) = a + b
	}
	implicit def appendInt = new Value[Int, Int] {
		def appendValue(a: Int, b: Int) = a + b
	}
	implicit def appendLong = new Value[Long, Long] {
		def appendValue(a: Long, b: Long) = a + b
	}
	implicit def appendDouble = new Value[Double, Double] {
		def appendValue(a: Double, b: Double) = a + b
	}
	implicit def appendClasspath: Sequence[Classpath, Seq[File], File] = new Sequence[Classpath, Seq[File], File] {
		def appendValues(a: Classpath, b: Seq[File]): Classpath = a ++ Attributed.blankSeq(b)
		def appendValue(a: Classpath, b: File): Classpath = a :+ Attributed.blank(b)
	}
}