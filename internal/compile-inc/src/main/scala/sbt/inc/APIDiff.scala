package sbt.inc

import xsbti.api.SourceAPI
import xsbt.api.ShowAPI
import xsbt.api.DefaultShowAPI._
import java.lang.reflect.Method
import java.util.{ List => JList }

/**
 * A class which computes diffs (unified diffs) between two textual representations of an API.
 *
 * Internally, it uses java-diff-utils library but it calls it through reflection so there's
 * no hard dependency on java-diff-utils.
 *
 * The reflective lookup of java-diff-utils library is performed in the constructor. Exceptions
 * thrown by reflection are passed as-is to the caller of the constructor.
 *
 * @throws ClassNotFoundException if difflib.DiffUtils class cannot be located
 * @throws LinkageError
 * @throws ExceptionInInitializerError
 */
private[inc] class APIDiff {

  import APIDiff._

  private val diffUtilsClass = Class.forName(diffUtilsClassName)
  // method signature: diff(List<?>, List<?>)
  private val diffMethod: Method =
    diffUtilsClass.getMethod(diffMethodName, classOf[JList[_]], classOf[JList[_]])

  private val generateUnifiedDiffMethod: Method = {
    val patchClass = Class.forName(patchClassName)
    // method signature: generateUnifiedDiff(String, String, List<String>, Patch, int)
    diffUtilsClass.getMethod(generateUnifiedDiffMethodName, classOf[String],
      classOf[String], classOf[JList[String]], patchClass, classOf[Int])
  }

  /**
   * Generates an unified diff between textual representations of `api1` and `api2`.
   */
  def generateApiDiff(fileName: String, api1: SourceAPI, api2: SourceAPI, contextSize: Int): String = {
    val api1Str = ShowAPI.show(api1)
    val api2Str = ShowAPI.show(api2)
    generateApiDiff(fileName, api1Str, api2Str, contextSize)
  }

  private def generateApiDiff(fileName: String, f1: String, f2: String, contextSize: Int): String = {
    assert((diffMethod != null) && (generateUnifiedDiffMethod != null), "APIDiff isn't properly initialized.")
    import scala.collection.JavaConverters._
    def asJavaList[T](it: Iterator[T]): java.util.List[T] = it.toSeq.asJava
    val f1Lines = asJavaList(f1.lines)
    val f2Lines = asJavaList(f2.lines)
    //val diff = DiffUtils.diff(f1Lines, f2Lines)
    val diff /*: Patch*/ = diffMethod.invoke(null, f1Lines, f2Lines)
    val unifiedPatch: JList[String] = generateUnifiedDiffMethod.invoke(null, fileName, fileName, f1Lines, diff,
      (contextSize: java.lang.Integer)).asInstanceOf[JList[String]]
    unifiedPatch.asScala.mkString("\n")
  }

}

private[inc] object APIDiff {
  private val diffUtilsClassName = "difflib.DiffUtils"
  private val patchClassName = "difflib.Patch"
  private val diffMethodName = "diff"
  private val generateUnifiedDiffMethodName = "generateUnifiedDiff"
}
