package xsbt

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
					CallbackTest(scalaVersion, files, Seq( "a.Super", "Super2", "x.Super3", "Super4"), Nil )  { (callback, _, _, _) =>
						val expected =
							(sub1File, "a.Sub1", "a.Super", false) ::
							(sub2File, "Sub2", "a.Super", false) ::
							(sup2File, "Super2", "Super2", false) ::
							(sub3File, "c.Sub3", "Super2", true) ::
							Nil
						(callback.foundSubclasses) must haveTheSameElementsAs(expected)
						(callback.invalidSuperclasses) must haveTheSameElementsAs(Seq("x.Super3", "Super4"))
					}
		}
	}
}