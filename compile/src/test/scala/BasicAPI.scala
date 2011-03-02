package sbt
package compiler

	import java.io.File
	import org.specs.Specification

object CheckBasicAPI extends Specification
{
	val basicName = new File("Basic.scala")
	val basicSource =
"""
package org.example {
	trait Parent[A] { def x: A }
	abstract class Child extends Parent[String]
	class S {
		def x = new { def y = new S { def z = new Child { def x = "asdf" } } }
		final val xconst = 3
		private[this] def zz: Seq[_] = Nil
		type L[M[_], P <: Int >: Int] = M[P]
		type Q = L[Option, Int]
	}
	object Basic
	trait Out {
		trait In
		def t: In
	}
}
package org.example3 {
	trait A extends Iterator[Int]
}
package org.example2 {
	trait ZZ[S] {
		val p: S
		val q: ZZZ[S] { val what: S }
		class A extends ZZZ[S] {
			val p = error("")
			val q = error("")
		}
	}
	trait ZZZ[T] extends ZZ[List[T]]
	trait ZZZZ extends ZZZ[Int]
	trait Z extends ZZZZ 
	trait P[S] {
		trait Q
		def x(s: S): S
		def q(v: Q): Q
		val u: Q
		val p: S
	}
	trait Out { self: P[String] =>
		trait In
		def z = p
		def t(i: In): In
		def y(q: Q): Q
	}
}
"""

	"Compiling should succeed" in {
		WithFiles(basicName -> basicSource){ files =>
			for(scalaVersion <- TestCompile.allVersions)
			{
				TestCompile(scalaVersion, files){ loader => Class.forName("org.example.Basic", false, loader) }
				true must beTrue // don't know how to just check that previous line completes without exception
			}
		}
	}
}