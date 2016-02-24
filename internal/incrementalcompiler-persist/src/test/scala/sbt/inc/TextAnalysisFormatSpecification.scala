package sbt
package inc

import xsbti.api.DependencyContext.DependencyByMemberRef
import xsbti.api.ExternalDependency
import xsbti.compile.{ MiniSetup, MiniOptions }
import sbt.internal.inc.{ Analysis, Exists, SourceInfos, TestCaseGenerators, TextAnalysisFormat }
import sbt.util.InterfaceUtil._

import java.io.{ BufferedReader, File, StringReader, StringWriter }
import scala.math.abs
import org.scalacheck._
import Gen._
import Prop._

object TextAnalysisFormatTest extends Properties("TextAnalysisFormat") {

  val nameHashing = true
  val dummyOutput = new xsbti.compile.SingleOutput { def outputDirectory: java.io.File = new java.io.File("/dummy") }
  val commonSetup = new MiniSetup(dummyOutput, new MiniOptions(Array(), Array()), "2.10.4", xsbti.compile.CompileOrder.Mixed, nameHashing,
    Array(t2(("key", "value"))))
  val commonHeader = """format version: 6
                    |output mode:
                    |1 items
                    |0 -> single
                    |output directories:
                    |1 items
                    |file:/output_dir -> file:/dummy
                    |compile options:
                    |0 items
                    |javac options:
                    |0 items
                    |compiler version:
                    |1 items
                    |0 -> 2.10.4
                    |compile order:
                    |1 items
                    |0 -> Mixed
                    |name hashing:
                    |1 items
                    |0 -> true
                    |extra:
                    |1 items
                    |key -> value""".stripMargin

  property("Write and read empty Analysis") = {

    val writer = new StringWriter
    val analysis = Analysis.empty(nameHashing)
    TextAnalysisFormat.write(writer, analysis, commonSetup)

    val result = writer.toString
    // println(result)

    val reader = new BufferedReader(new StringReader(result))

    val (readAnalysis: Analysis, readSetup) = TextAnalysisFormat.read(reader)

    compare(analysis, readAnalysis) && result.startsWith(commonHeader)
  }

  property("Write and read simple Analysis") = {

    import TestCaseGenerators._

    def f(s: String) = new File("/temp/" + s)
    val aScala = f("A.scala")
    val bScala = f("B.scala")
    val aSource = genSource("A" :: "A$" :: Nil).sample.get
    val bSource = genSource("B" :: "B$" :: Nil).sample.get
    val cSource = genSource("C" :: Nil).sample.get
    val exists = new Exists(true)
    val sourceInfos = SourceInfos.makeInfo(Nil, Nil)

    var analysis = Analysis.empty(nameHashing)
    analysis = analysis.addSource(aScala, aSource, exists, sourceInfos,
      products = List((f("A.class"), "A", exists), (f("A$.class"), "A$", exists)),
      internalDeps = Nil,
      externalDeps = List(new ExternalDependency(aScala, "C", cSource, DependencyByMemberRef)),
      binaryDeps = List((f("x.jar"), "x", exists)))

    val writer = new StringWriter

    TextAnalysisFormat.write(writer, analysis, commonSetup)

    val result = writer.toString
    // println(result)

    val reader = new BufferedReader(new StringReader(result))

    val (readAnalysis: Analysis, readSetup) = TextAnalysisFormat.read(reader)

    compare(analysis, readAnalysis) && result.startsWith(commonHeader)
  }

  property("Write and read complex Analysis") = forAllNoShrink(TestCaseGenerators.genAnalysis(nameHashing)) { analysis: Analysis =>
    val writer = new StringWriter

    TextAnalysisFormat.write(writer, analysis, commonSetup)

    val result = writer.toString

    val reader = new BufferedReader(new StringReader(result))

    val (readAnalysis: Analysis, readSetup) = TextAnalysisFormat.read(reader)

    compare(analysis, readAnalysis) && result.startsWith(commonHeader)
  }

  // Compare two analyses with useful labelling when they aren't equal.
  private[this] def compare(left: Analysis, right: Analysis): Prop = {
    s" LEFT: $left" |:
      s"RIGHT: $right" |:
      s"STAMPS EQUAL: ${left.stamps == right.stamps}" |:
      s"APIS EQUAL: ${left.apis == right.apis}" |:
      s"RELATIONS EQUAL: ${left.relations == right.relations}" |:
      "UNEQUAL" |:
      (left == right)
  }
}
