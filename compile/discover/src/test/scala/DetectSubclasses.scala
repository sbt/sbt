package sbt
package compile

	import java.io.File
	import org.specs.Specification

object DetectSubclasses extends Specification
{
	val sources =
		("a/Super.scala" -> "package a; trait Super") ::
		("a/Super2.scala" -> "class Super2") ::
		("b/Middle.scala" -> "package y.w; trait Mid extends a.Super") ::
		("b/Sub1.scala" -> "package a; class Sub1 extends y.w.Mid") ::
		("b/Sub2.scala" -> "final class Sub2 extends a.Super") ::
		("Sub3.scala" -> "private class F extends a.Super; package c { object Sub3 extends Super2 }") ::
		Nil

	"Analysis plugin should detect subclasses" in {
		WithFiles(sources.map{case (file, content) => (new File(file), content)} : _*)
		{
			case files @ Seq(supFile, sup2File, midFile, sub1File, sub2File, sub3File) =>
				for(scalaVersion <- TestCompile.allVersions)
					CallbackTest.simple(scalaVersion, files)  { callback =>
						val expected =
							(sub1File, "a.Sub1", Set("a.Super"), false) ::
							(sub2File, "Sub2", Set("a.Super"), false) ::
							(sup2File, "Super2", Set("Super2"), false) ::
							(sub3File, "c.Sub3", Set("Super2"), true) ::
							Nil
						val actual = subclasses(callback).toSet
						val actualOnly = actual -- expected
						val expectedOnly = expected.toSet -- actual
						assert(actualOnly.isEmpty, "Actual only: " + actualOnly)
						assert(expectedOnly.isEmpty , "Expected only: " + expectedOnly)
					}
		}
	}
	def subclasses(callback: xsbti.TestCallback): Seq[(File, String, Set[String], Boolean)] =
		for( (file, src) <- callback.apis.toSeq; (definition, discovered) <- Discovery(subclassNames, Set.empty)(src.definitions) if !discovered.isEmpty ) yield
			(file, definition.name, discovered.baseClasses, discovered.isModule)
	def subclassNames = Set( "a.Super", "Super2", "x.Super3", "Super4")
}