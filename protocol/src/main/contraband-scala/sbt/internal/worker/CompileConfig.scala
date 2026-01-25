/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class CompileConfig private (
  val fileConverterConfig: sbt.internal.worker.FileConverterConfig,
  val scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig,
  val bridgeJars: Vector[java.net.URI],
  val sources: Vector[String],
  val externalDependencyJars: Vector[String],
  val output: java.net.URI,
  val analysisFile: java.net.URI,
  val earlyJarPath: Option[java.net.URI],
  val scalacOptions: Vector[String],
  val javacOptions: Vector[String],
  val maxErrors: Int,
  val analysisMap: Vector[sbt.internal.worker.HVFRURI]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CompileConfig => (this.fileConverterConfig == x.fileConverterConfig) && (this.scalaInstanceConfig == x.scalaInstanceConfig) && (this.bridgeJars == x.bridgeJars) && (this.sources == x.sources) && (this.externalDependencyJars == x.externalDependencyJars) && (this.output == x.output) && (this.analysisFile == x.analysisFile) && (this.earlyJarPath == x.earlyJarPath) && (this.scalacOptions == x.scalacOptions) && (this.javacOptions == x.javacOptions) && (this.maxErrors == x.maxErrors) && (this.analysisMap == x.analysisMap)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.CompileConfig".##) + fileConverterConfig.##) + scalaInstanceConfig.##) + bridgeJars.##) + sources.##) + externalDependencyJars.##) + output.##) + analysisFile.##) + earlyJarPath.##) + scalacOptions.##) + javacOptions.##) + maxErrors.##) + analysisMap.##)
  }
  override def toString: String = {
    "CompileConfig(" + fileConverterConfig + ", " + scalaInstanceConfig + ", " + bridgeJars + ", " + sources + ", " + externalDependencyJars + ", " + output + ", " + analysisFile + ", " + earlyJarPath + ", " + scalacOptions + ", " + javacOptions + ", " + maxErrors + ", " + analysisMap + ")"
  }
  private def copy(fileConverterConfig: sbt.internal.worker.FileConverterConfig = fileConverterConfig, scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig = scalaInstanceConfig, bridgeJars: Vector[java.net.URI] = bridgeJars, sources: Vector[String] = sources, externalDependencyJars: Vector[String] = externalDependencyJars, output: java.net.URI = output, analysisFile: java.net.URI = analysisFile, earlyJarPath: Option[java.net.URI] = earlyJarPath, scalacOptions: Vector[String] = scalacOptions, javacOptions: Vector[String] = javacOptions, maxErrors: Int = maxErrors, analysisMap: Vector[sbt.internal.worker.HVFRURI] = analysisMap): CompileConfig = {
    new CompileConfig(fileConverterConfig, scalaInstanceConfig, bridgeJars, sources, externalDependencyJars, output, analysisFile, earlyJarPath, scalacOptions, javacOptions, maxErrors, analysisMap)
  }
  def withFileConverterConfig(fileConverterConfig: sbt.internal.worker.FileConverterConfig): CompileConfig = {
    copy(fileConverterConfig = fileConverterConfig)
  }
  def withScalaInstanceConfig(scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig): CompileConfig = {
    copy(scalaInstanceConfig = scalaInstanceConfig)
  }
  def withBridgeJars(bridgeJars: Vector[java.net.URI]): CompileConfig = {
    copy(bridgeJars = bridgeJars)
  }
  def withSources(sources: Vector[String]): CompileConfig = {
    copy(sources = sources)
  }
  def withExternalDependencyJars(externalDependencyJars: Vector[String]): CompileConfig = {
    copy(externalDependencyJars = externalDependencyJars)
  }
  def withOutput(output: java.net.URI): CompileConfig = {
    copy(output = output)
  }
  def withAnalysisFile(analysisFile: java.net.URI): CompileConfig = {
    copy(analysisFile = analysisFile)
  }
  def withEarlyJarPath(earlyJarPath: Option[java.net.URI]): CompileConfig = {
    copy(earlyJarPath = earlyJarPath)
  }
  def withEarlyJarPath(earlyJarPath: java.net.URI): CompileConfig = {
    copy(earlyJarPath = Option(earlyJarPath))
  }
  def withScalacOptions(scalacOptions: Vector[String]): CompileConfig = {
    copy(scalacOptions = scalacOptions)
  }
  def withJavacOptions(javacOptions: Vector[String]): CompileConfig = {
    copy(javacOptions = javacOptions)
  }
  def withMaxErrors(maxErrors: Int): CompileConfig = {
    copy(maxErrors = maxErrors)
  }
  def withAnalysisMap(analysisMap: Vector[sbt.internal.worker.HVFRURI]): CompileConfig = {
    copy(analysisMap = analysisMap)
  }
}
object CompileConfig {
  
  def apply(fileConverterConfig: sbt.internal.worker.FileConverterConfig, scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig, bridgeJars: Vector[java.net.URI], sources: Vector[String], externalDependencyJars: Vector[String], output: java.net.URI, analysisFile: java.net.URI, earlyJarPath: Option[java.net.URI], scalacOptions: Vector[String], javacOptions: Vector[String], maxErrors: Int, analysisMap: Vector[sbt.internal.worker.HVFRURI]): CompileConfig = new CompileConfig(fileConverterConfig, scalaInstanceConfig, bridgeJars, sources, externalDependencyJars, output, analysisFile, earlyJarPath, scalacOptions, javacOptions, maxErrors, analysisMap)
  def apply(fileConverterConfig: sbt.internal.worker.FileConverterConfig, scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig, bridgeJars: Vector[java.net.URI], sources: Vector[String], externalDependencyJars: Vector[String], output: java.net.URI, analysisFile: java.net.URI, earlyJarPath: java.net.URI, scalacOptions: Vector[String], javacOptions: Vector[String], maxErrors: Int, analysisMap: Vector[sbt.internal.worker.HVFRURI]): CompileConfig = new CompileConfig(fileConverterConfig, scalaInstanceConfig, bridgeJars, sources, externalDependencyJars, output, analysisFile, Option(earlyJarPath), scalacOptions, javacOptions, maxErrors, analysisMap)
}
