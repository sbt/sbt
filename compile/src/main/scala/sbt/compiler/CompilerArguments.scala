/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt
package compiler

import xsbti.ArtifactInfo
import scala.util
import java.io.File
import CompilerArguments.{ abs, absString, BootClasspathOption }

/**
 * Forms the list of options that is passed to the compiler from the required inputs and other options.
 * The directory containing scala-library.jar and scala-compiler.jar (scalaLibDirectory) is required in
 * order to add these jars to the boot classpath. The 'scala.home' property must be unset because Scala
 * puts jars in that directory on the bootclasspath.  Because we use multiple Scala versions,
 * this would lead to compiling against the wrong library jar.
 */
final class CompilerArguments(scalaInstance: xsbti.compile.ScalaInstance, cp: xsbti.compile.ClasspathOptions) {
  def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: Option[File], options: Seq[String]): Seq[String] =
    {
      checkScalaHomeUnset()
      val cpWithCompiler = finishClasspath(classpath)
      // Scala compiler's treatment of empty classpath is troublesome (as of 2.9.1).
      // We append a random dummy element as workaround.
      val dummy = "dummy_" + Integer.toHexString(util.Random.nextInt)
      val classpathOption = Seq("-classpath", if (cpWithCompiler.isEmpty) dummy else absString(cpWithCompiler))
      val outputOption = outputDirectory map { out => Seq("-d", out.getAbsolutePath) } getOrElse Seq()
      options ++ outputOption ++ bootClasspathOption(hasLibrary(classpath)) ++ classpathOption ++ abs(sources)
    }
  def finishClasspath(classpath: Seq[File]): Seq[File] =
    filterLibrary(classpath) ++ include(cp.compiler, scalaInstance.compilerJar) ++ include(cp.extra, scalaInstance.otherJars: _*)
  private[this] def include(flag: Boolean, jars: File*) = if (flag) jars else Nil
  private[this] def abs(files: Seq[File]) = files.map(_.getAbsolutePath).sortWith(_ < _)
  private[this] def checkScalaHomeUnset(): Unit = {
    val scalaHome = System.getProperty("scala.home")
    assert((scalaHome eq null) || scalaHome.isEmpty, "'scala.home' should not be set (was " + scalaHome + ")")
  }
  def createBootClasspathFor(classpath: Seq[File]) = createBootClasspath(hasLibrary(classpath) || cp.compiler || cp.extra)

  /** Add the correct Scala library jar to the boot classpath if `addLibrary` is true.*/
  def createBootClasspath(addLibrary: Boolean) =
    {
      val originalBoot = System.getProperty("sun.boot.class.path", "")
      if (addLibrary) {
        val newBootPrefix = if (originalBoot.isEmpty) "" else originalBoot + File.pathSeparator
        newBootPrefix + scalaInstance.libraryJar.getAbsolutePath
      } else
        originalBoot
    }
  def filterLibrary(classpath: Seq[File]) = if (cp.filterLibrary) classpath filterNot isScalaLibrary else classpath
  def hasLibrary(classpath: Seq[File]) = classpath exists isScalaLibrary
  private[this] val isScalaLibrary: File => Boolean = file => {
    val name = file.getName
    (name contains ArtifactInfo.ScalaLibraryID) || file.getName == scalaInstance.libraryJar.getName
  }
  def bootClasspathOption(addLibrary: Boolean) = if (cp.autoBoot) Seq(BootClasspathOption, createBootClasspath(addLibrary)) else Nil
  def bootClasspath(addLibrary: Boolean) = if (cp.autoBoot) IO.parseClasspath(createBootClasspath(addLibrary)) else Nil
  def bootClasspathFor(classpath: Seq[File]) = bootClasspath(hasLibrary(classpath))

  import Path._
  def extClasspath: Seq[File] = (IO.parseClasspath(System.getProperty("java.ext.dirs")) * "*.jar").get
}
object CompilerArguments {
  val BootClasspathOption = "-bootclasspath"
  def abs(files: Seq[File]): Seq[String] = files.map(_.getAbsolutePath)
  def abs(files: Set[File]): Seq[String] = abs(files.toSeq)
  def absString(files: Seq[File]): String = abs(files).mkString(File.pathSeparator)
  def absString(files: Set[File]): String = absString(files.toSeq)
}
