package sbt
package inc

import java.io.{ File, PrintWriter }
import sbt.io.Path._
import sbt.io.IO
import sbt.util.Logger
import xsbti.Reporter
import sbt.internal.inc.javac.JavaTools

object Doc {
  // sources, classpath, outputDirectory, options, log, reporter
  // type Gen = (Seq[File], Seq[File], File, Seq[String], Logger, Reporter) => Unit

  // def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler): RawCompileLike.Gen =
  //   scaladoc(label, cache, compiler, Seq())
  // def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler, fileInputOptions: Seq[String]): RawCompileLike.Gen =
  //   RawCompileLike.cached(cache, fileInputOptions, RawCompileLike.prepare(label + " Scala API documentation", compiler.doc))
  def cachedJavadoc(label: String, cache: File, doc: JavaTools): JavaDoc =
    new JavaDoc {
      def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String], log: Logger, reporter: Reporter): Unit = {
        val opts = List("-d", outputDirectory.getAbsolutePath)
        doc.doc(sources, opts ::: options)(log, reporter)
        ()
      }
    }

  // Doc.cached(cache, prepare(label + " Java API documentation", (sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger, reporter: Reporter) => {
  //   doc.doc(sources, options)(log, reporter)
  // }))

  // private[sbt] def prepare(description: String, doDoc: Gen): Gen = (sources, classpath, outputDirectory, options, log, reporter) =>
  //   {
  //     if (sources.isEmpty) log.info("No sources available, skipping " + description + "...")
  //     else {
  //       log.info(description.capitalize + " to " + outputDirectory.absolutePath + "...")
  //       IO.delete(outputDirectory)
  //       IO.createDirectory(outputDirectory)
  //       doDoc(sources, classpath, outputDirectory, options, log, reporter)
  //       log.info(description.capitalize + " successful.")
  //     }
  //   }
  // private[sbt] def cached(cache: File, doDoc: Gen): Gen =
  //   (sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger, reporter: Reporter) => {
  //     type Inputs = FilesInfo[HashFileInfo] :+: FilesInfo[ModifiedFileInfo] :+: Seq[File] :+: File :+: Seq[String] :+: HNil
  //     val inputs: Inputs = hash(sources.toSet) :+: lastModified(classpath.toSet) :+: classpath :+: outputDirectory :+: options :+: HNil
  //     implicit val stringEquiv: Equiv[String] = defaultEquiv
  //     implicit val fileEquiv: Equiv[File] = defaultEquiv
  //     val cachedDoc = inputChanged(cache / "inputs") { (inChanged, in: Inputs) =>
  //       outputChanged(cache / "output") { (outChanged, outputs: FilesInfo[PlainFileInfo]) =>
  //         if (inChanged || outChanged) doDoc(sources, classpath, outputDirectory, options, log, reporter)
  //         else log.debug("Doc uptodate: " + outputDirectory.getAbsolutePath)
  //       }
  //     }
  //     cachedDoc(inputs)(() => exists(outputDirectory.allPaths.get.toSet))
  //   }

  // val javaSourcesOnly: File => Boolean = _.getName.endsWith(".java")

  trait JavaDoc {
    def run(sources: List[File], classpath: List[File], outputDirectory: File, options: List[String], log: Logger, reporter: Reporter): Unit
  }
}
