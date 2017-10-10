/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package compiler

import scala.language.reflectiveCalls
import org.scalacheck._
import Prop._
import scala.tools.nsc.reporters.StoreReporter

import sbt.io.IO

class EvalTest extends Properties("eval") {
  private[this] lazy val reporter = new StoreReporter
  import reporter.{ ERROR, Info }
  private[this] lazy val eval = new Eval(_ => reporter, None)

  property("inferred integer") = forAll { (i: Int) =>
    val result = eval.eval(i.toString)
    (label("Value", value(result)) |: (value(result) == i)) &&
    (label("Type", value(result)) |: (result.tpe == IntType)) &&
    (label("Files", result.generated) |: (result.generated.isEmpty))
  }

  property("explicit integer") = forAll { (i: Int) =>
    val result = eval.eval(i.toString, tpeName = Some(IntType))
    (label("Value", value(result)) |: (value(result) == i)) &&
    (label("Type", result.tpe) |: (result.tpe == IntType)) &&
    (label("Files", result.generated) |: (result.generated.isEmpty))
  }

  property("type mismatch") = forAll { (i: Int, l: Int) =>
    val line = math.abs(l)
    val src = "mismatch"
    throws(classOf[RuntimeException])(
      eval.eval(i.toString, tpeName = Some(BooleanType), line = line, srcName = src)) &&
    hasErrors(line + 1, src)
  }

  property("backed local class") = forAll { (i: Int) =>
    IO.withTemporaryDirectory { dir =>
      val eval = new Eval(_ => reporter, backing = Some(dir))
      val result = eval.eval(local(i))
      val v = value(result).asInstanceOf[{ def i: Int }].i
      (label("Value", v) |: (v == i)) &&
      (label("Type", result.tpe) |: (result.tpe == LocalType)) &&
      (label("Files", result.generated) |: result.generated.nonEmpty)
    }
  }

  val ValTestNames = Set("x", "a")
  val ValTestContent = """
val x: Int = {
  val y: Int = 4
  y
}
val z: Double = 3.0
val a = 9
val p = {
   object B { val i = 3 }
   class C { val j = 4 }
   "asdf"
}
"""

  property("val test") = secure {
    val defs = (ValTestContent, 1 to 7) :: Nil
    val res =
      eval.evalDefinitions(defs, new EvalImports(Nil, ""), "<defs>", None, "scala.Int" :: Nil)
    label("Val names", res.valNames) |: (res.valNames.toSet == ValTestNames)
  }

  property("explicit import") = forAll(testImport("import math.abs" :: Nil))
  property("wildcard import") = forAll(testImport("import math._" :: Nil))
  property("comma-separated imports") = forAll(
    testImport("import annotation._, math._, meta._" :: Nil))
  property("multiple imports") = forAll(
    testImport("import annotation._" :: "import math._" :: "import meta._" :: Nil))

  private[this] def testImport(imports: Seq[String]): Int => Prop =
    i =>
      value(eval.eval("abs(" + i + ")", new EvalImports(imports.zipWithIndex, "imp"))) == math.abs(
        i)

  private[this] def local(i: Int) = "{ class ETest(val i: Int); new ETest(" + i + ") }"
  val LocalType = "AnyRef{val i: Int}"

  private[this] def value(r: EvalResult) = r.getValue(getClass.getClassLoader)
  private[this] def hasErrors(line: Int, src: String) = {
    val is = reporter.infos
    ("Has errors" |: is.nonEmpty) &&
    all(is.toSeq.map(validPosition(line, src)): _*)
  }
  private[this] def validPosition(line: Int, src: String)(i: Info) = {
    val nme = i.pos.source.file.name
    (label("Severity", i.severity) |: (i.severity == ERROR)) &&
    (label("Line", i.pos.line) |: (i.pos.line == line)) &&
    (label("Name", nme) |: (nme == src))
  }
  val IntType = "Int"
  val BooleanType = "Boolean"

  def label(s: String, value: Any) = s + " (" + value + ")"
}
