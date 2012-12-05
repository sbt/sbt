package xsbt.boot

import java.io.File
import java.util.Arrays.{equals => arrEquals}
import org.scalacheck._

object PreTest extends Properties("Pre")
{
	import Pre._
	property("isEmpty") = Prop.forAll( (s: String) => (s.isEmpty == isEmpty(s)) )
	property("isNonEmpty") = Prop.forAll( (s: String) => (isEmpty(s) != isNonEmpty(s)) )
	property("assert true") = { assert(true); true }
	property("assert false") = Prop.throws(assert(false), classOf[AssertionError])
	property("assert true with message") = Prop.forAll { (s: String) => assert(true, s); true }
	property("assert false with message") = Prop.forAll( (s: String) => Prop.throws(assert(false, s), classOf[AssertionError] ) )
	property("require false") = Prop.forAll( (s: String) => Prop.throws(require(false, s), classOf[IllegalArgumentException]) )
	property("require true") = Prop.forAll { (s: String) => require(true, s); true }
	property("error") = Prop.forAll( (s: String) => Prop.throws(error(s), classOf[BootException]) )
	property("toBoolean") = Prop.forAll( (s: String) => trap(toBoolean(s)) == trap(java.lang.Boolean.parseBoolean(s)) )
	property("toArray") = Prop.forAll( (list: List[Int]) => arrEquals(list.toArray, toArray(list)) )
	property("toArray") = Prop.forAll( (list: List[String]) => objArrEquals(list.toArray, toArray(list)) )
	property("concat") = Prop.forAll(genFiles, genFiles) { (a: Array[File], b: Array[File]) => (a ++ b) sameElements concat(a, b) }
	property("array") = Prop.forAll(genFiles) { (a: Array[File]) => array(a.toList : _*) sameElements Array(a: _*) }

	implicit lazy val arbFile: Arbitrary[File] = Arbitrary { for(i <- Arbitrary.arbitrary[Int] ) yield new File(i.toString) }
	implicit lazy val genFiles: Gen[Array[File]] = Arbitrary.arbitrary[Array[File]]

	def trap[T](t: => T): Option[T] = try { Some(t) } catch { case e: Exception => None }

	private[this] def objArrEquals[T <: AnyRef](a: Array[T], b: Array[T]): Boolean =
		arrEquals(a.asInstanceOf[Array[AnyRef]], b.asInstanceOf[Array[AnyRef]])
}