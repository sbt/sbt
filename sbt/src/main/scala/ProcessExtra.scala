package sbt

import java.lang.{ Process => JProcess, ProcessBuilder => JProcessBuilder }

trait ProcessExtra {
  import scala.sys.process._
  import scala.sys.process.Process._
  implicit def builderToProcess(builder: JProcessBuilder): ProcessBuilder = apply(builder)
  implicit def fileToProcess(file: File): ProcessBuilder.FileBuilder = apply(file)
  implicit def urlToProcess(url: URL): ProcessBuilder.URLBuilder = apply(url)
  // @deprecated("Use string interpolation", "0.13.0")
  // implicit def xmlToProcess(command: scala.xml.Elem): ProcessBuilder = apply(command)
  implicit def buildersToProcess[T](builders: Seq[T])(implicit convert: T => ProcessBuilder.Source): Seq[ProcessBuilder.Source] = applySeq(builders)

  implicit def stringToProcess(command: String): ProcessBuilder = apply(command)
  implicit def stringSeqToProcess(command: Seq[String]): ProcessBuilder = apply(command)
}
