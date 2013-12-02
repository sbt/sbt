package sbt
package inc

import java.io.File
import scala.math.abs
import sbt.inc.TestCaseGenerators._
import org.scalacheck._
import Gen._
import Prop._


object AnalysisTest extends Properties("Analysis") {
	// Merge and split a hard-coded trivial example.
	property("Simple Merge and Split") = {
		def f(s: String) = new File(s)
		val aScala = f("A.scala")
		val bScala = f("B.scala")
		val aSource = genSource("A" :: "A$" :: Nil).sample.get
		val bSource = genSource("B" :: "B$" :: Nil).sample.get
		val cSource = genSource("C" :: Nil).sample.get
		val exists = new Exists(true)
		val sourceInfos = SourceInfos.makeInfo(Nil, Nil)

		// a
		var a = Analysis.Empty
		a = a.addProduct(aScala, f("A.class"), exists, "A")
		a = a.addProduct(aScala, f("A$.class"), exists, "A$")
		a = a.addSource(aScala, aSource, exists, Nil, Nil, sourceInfos)
		a = a.addBinaryDep(aScala, f("x.jar"), "x", exists)
		a = a.addExternalDep(aScala, "C", cSource, inherited=false)

		// b
		var b = Analysis.Empty
		b = b.addProduct(bScala, f("B.class"), exists, "B")
		b = b.addProduct(bScala, f("B$.class"), exists, "B$")
		b = b.addSource(bScala, bSource, exists, Nil, Nil, sourceInfos)
		b = b.addBinaryDep(bScala, f("x.jar"), "x", exists)
		b = b.addBinaryDep(bScala, f("y.jar"), "y", exists)
		b = b.addExternalDep(bScala, "A", aSource, inherited=true)

		// ab
		var ab = Analysis.Empty
		ab = ab.addProduct(aScala, f("A.class"), exists, "A")
		ab = ab.addProduct(aScala, f("A$.class"), exists, "A$")
		ab = ab.addProduct(bScala, f("B.class"), exists, "B")
		ab = ab.addProduct(bScala, f("B$.class"), exists, "B$")
		ab = ab.addSource(aScala, aSource, exists, Nil, Nil, sourceInfos)
		ab = ab.addSource(bScala, bSource, exists, aScala :: Nil, aScala :: Nil, sourceInfos)
		ab = ab.addBinaryDep(aScala, f("x.jar"), "x", exists)
		ab = ab.addBinaryDep(bScala, f("x.jar"), "x", exists)
		ab = ab.addBinaryDep(bScala, f("y.jar"), "y", exists)
		ab = ab.addExternalDep(aScala, "C", cSource, inherited=false)

		val split: Map[String, Analysis] = ab.groupBy({ f: File => f.getName.substring(0, 1) })

		val aSplit = split.getOrElse("A", Analysis.Empty)
		val bSplit = split.getOrElse("B", Analysis.Empty)

		val merged = Analysis.merge(a :: b :: Nil)

		("split(AB)(A) == A" |: compare(a, aSplit)) &&
		("split(AB)(B) == B" |: compare(b, bSplit)) &&
		("merge(A, B) == AB" |: compare(merged, ab))
	}

	// Merge and split large, generated examples.
	// Mustn't shrink, as the default Shrink[Int] doesn't respect the lower bound of choose(), which will cause
	// a divide-by-zero error masking the original error.
	property("Complex Merge and Split") = forAllNoShrink(genAnalysis, choose(1, 10)) { (analysis: Analysis, numSplits: Int) =>
		val grouped: Map[Int, Analysis] = analysis.groupBy({ f: File => abs(f.hashCode()) % numSplits})
		def getGroup(i: Int): Analysis = grouped.getOrElse(i, Analysis.Empty)
		val splits = (Range(0, numSplits) map getGroup).toList

		val merged: Analysis = Analysis.merge(splits)
		"Merge all" |: compare(analysis, merged)
	}

	// Compare two analyses with useful labelling when they aren't equal.
	private[this] def compare(left: Analysis, right: Analysis): Prop =
		s" LEFT: $left" |:
		s"RIGHT: $right" |:
		s"STAMPS EQUAL: ${left.stamps == right.stamps}" |:
		s"APIS EQUAL: ${left.apis == right.apis}" |:
		s"RELATIONS EQUAL: ${left.relations == right.relations}" |:
		"UNEQUAL" |:
		(left == right)
}
