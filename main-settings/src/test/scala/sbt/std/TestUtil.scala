/*
 * This file has been copy-pasted from spores.
 * https://github.com/scalacenter/spores/blob/master/core/src/test/scala/scala/spores/TestUtil.scala
 */

package sbt.std

import scala.reflect._

object TestUtil {
  import tools.reflect.{ ToolBox, ToolBoxError }

  def intercept[T <: Throwable: ClassTag](test: => Any): T = {
    try {
      test
      throw new Exception(s"Expected exception ${classTag[T]}")
    } catch {
      case t: Throwable =>
        if (classTag[T].runtimeClass != t.getClass) throw t
        else t.asInstanceOf[T]
    }
  }

  def eval(code: String, compileOptions: String = ""): Any = {
    val tb = mkToolbox(compileOptions)
    tb.eval(tb.parse(code))
  }

  def mkToolbox(compileOptions: String = ""): ToolBox[_ <: scala.reflect.api.Universe] = {
    val m = scala.reflect.runtime.currentMirror
    import scala.tools.reflect.ToolBox
    m.mkToolBox(options = compileOptions)
  }

  lazy val toolboxClasspath: String = {
    val resource = getClass.getClassLoader.getResource("toolbox.classpath")
    val classpathFile = scala.io.Source.fromFile(resource.toURI)
    val completeSporesCoreClasspath = classpathFile.getLines.mkString
    completeSporesCoreClasspath
  }

  def expectError(errorSnippet: String,
                  compileOptions: String = "-Xmacro-settings:debug-spores",
                  baseCompileOptions: String = s"-cp $toolboxClasspath")(code: String): Unit = {
    val errorMessage = intercept[ToolBoxError] {
      eval(code, s"$compileOptions $baseCompileOptions")
    }.getMessage
    val userMessage =
      s"""
         |FOUND: $errorMessage
         |EXPECTED: $errorSnippet
      """.stripMargin
    assert(errorMessage.contains(errorSnippet), userMessage)
  }
}
