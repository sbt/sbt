package sbt
package compile

	import java.io.File
	import org.specs.Specification

object DetectAnnotations extends Specification
{
	val sources =
		("c/A.scala" -> "package c; class A(x: Int, y: Int) extends Annotation") ::
		("B.scala" -> "class B extends Annotation") ::
		("d/C.scala" -> "package d; class C extends Annotation") ::
		("a/Super1.scala" -> "package a; trait Super1") ::
		("a/Super2.scala" -> "package a; @c.A(3,4) trait Super2") ::
		("Super3.scala" -> "@B class Super3") ::
		("a/Super4.scala" -> "package a; trait Super4 { @d.C def test = () }") ::
		("b/Middle.scala" -> "package y.w; trait Mid extends a.Super2") ::
		("b/Sub1.scala" -> "class Sub1 extends Super3 with y.w.Mid") ::
		("b/Sub2.scala" -> "final class Sub2 extends a.Super1") ::
		("b/Sub3.scala" -> "@B @c.A(3,4) final class Sub3 extends a.Super1") ::
		("d/Sub4.scala" -> "@B private class Sub4 extends a.Super1") ::
		("d/Sub5.scala" -> "@B protected class Sub5 extends a.Super1") ::
		("d/Sub6.scala" -> "@B abstract class Sub6 extends a.Super1") ::
		("d/Sub7.scala" -> "class Sub7 extends a.Super4") ::
		("d/Sub8.scala" -> "class Sub8 { @c.A(5,6) def test(s: Int) = s }") ::
		("d/Sub9.scala" -> "object Sub9 { @B def test(s: String) = s }") ::
		("d/SubA.scala" -> "object SubA { @c.A(3,3) def test = () }\nclass SubA { @B private def test = 6 }") ::
		("d/SubB.scala" -> "object SubB { @c.A(3,3) def test = 3 }\nclass SubB { @d.C def test = () }") ::
		Nil

	"Analysis plugin should detect annotations" in {
		WithFiles(sources.map{case (file, content) => (new File(file), content)} : _*)
		{
			case files @ Seq(a, b, c, sup1File, sup2File, sup3File, sup4File, midFile, sub1File, sub2File, sub3File, sub4File, sub5File, sub6File, sub7File, sub8File, sub9File, subAFile, subBFile) =>
				for(scalaVersion <- TestCompile.allVersions)
					CallbackTest.simple(scalaVersion, files)  { callback =>
						val expected =
							(sup3File, "Super3", Set("B"), false) ::
							(sub3File, "Sub3", Set("B", "c.A"), false) ::
							(sub7File, "Sub7", Set("d.C"), false) ::
							(sub8File, "Sub8", Set("c.A"), false) ::
							(sub9File, "Sub9", Set("B"), true) ::
							(subAFile, "SubA", Set("c.A"), true) ::
							(subBFile, "SubB", Set("c.A"), true) ::
							(subBFile, "SubB", Set("d.C"), false) ::
							Nil
						val actual = subclasses(callback).toSet
						val actualOnly = (actual -- expected)
						val expectedOnly = (expected.toSet -- actual)
						expectedOnly must beEmpty
						actualOnly must beEmpty
					}
		}
	}
	def subclasses(callback: xsbti.TestCallback): Seq[(File, String, Set[String], Boolean)] =
		for( (file, src) <- callback.apis.toSeq; (definition, discovered) <- Discovery(Set.empty, annotationNames)(src.definitions) if !discovered.isEmpty ) yield
			(file, definition.name, discovered.annotations, discovered.isModule)

	def annotationNames = Set("c.A", "B", "d.C")
}