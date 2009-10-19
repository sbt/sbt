package xsbt.boot

import org.scalacheck._
import Prop._

object CacheTest extends Properties("Cache")
{
	implicit val functions: Arbitrary[Int => Int] = Arbitrary { Gen.elements(identity[Int], i => -i, i=>i/2, i => i+1) }

	property("Cache") = Prop.forAll { (key: Int, keys: List[Int], map: Int => Int) =>
		val cache = new Cache(map)
		def toProperty(key: Int) = ("Key "  + key) |: ("Value: " + map(key)) |: (cache.apply(key) == map(key))
		Prop.all( keys.map(toProperty) : _*)
	}
}