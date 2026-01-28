/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
/** Configuration for forked console. */
final class ConsoleConfig private (
  val scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig,
  val bridgeJars: Vector[java.net.URI],
  val externalDependencyJars: Vector[String],
  val scalacOptions: Vector[String],
  val initialCommands: String,
  val cleanupCommands: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ConsoleConfig => (this.scalaInstanceConfig == x.scalaInstanceConfig) && (this.bridgeJars == x.bridgeJars) && (this.externalDependencyJars == x.externalDependencyJars) && (this.scalacOptions == x.scalacOptions) && (this.initialCommands == x.initialCommands) && (this.cleanupCommands == x.cleanupCommands)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.ConsoleConfig".##) + scalaInstanceConfig.##) + bridgeJars.##) + externalDependencyJars.##) + scalacOptions.##) + initialCommands.##) + cleanupCommands.##)
  }
  override def toString: String = {
    "ConsoleConfig(" + scalaInstanceConfig + ", " + bridgeJars + ", " + externalDependencyJars + ", " + scalacOptions + ", " + initialCommands + ", " + cleanupCommands + ")"
  }
  private def copy(scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig = scalaInstanceConfig, bridgeJars: Vector[java.net.URI] = bridgeJars, externalDependencyJars: Vector[String] = externalDependencyJars, scalacOptions: Vector[String] = scalacOptions, initialCommands: String = initialCommands, cleanupCommands: String = cleanupCommands): ConsoleConfig = {
    new ConsoleConfig(scalaInstanceConfig, bridgeJars, externalDependencyJars, scalacOptions, initialCommands, cleanupCommands)
  }
  def withScalaInstanceConfig(scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig): ConsoleConfig = {
    copy(scalaInstanceConfig = scalaInstanceConfig)
  }
  def withBridgeJars(bridgeJars: Vector[java.net.URI]): ConsoleConfig = {
    copy(bridgeJars = bridgeJars)
  }
  def withExternalDependencyJars(externalDependencyJars: Vector[String]): ConsoleConfig = {
    copy(externalDependencyJars = externalDependencyJars)
  }
  def withScalacOptions(scalacOptions: Vector[String]): ConsoleConfig = {
    copy(scalacOptions = scalacOptions)
  }
  def withInitialCommands(initialCommands: String): ConsoleConfig = {
    copy(initialCommands = initialCommands)
  }
  def withCleanupCommands(cleanupCommands: String): ConsoleConfig = {
    copy(cleanupCommands = cleanupCommands)
  }
}
object ConsoleConfig {
  
  def apply(scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig, bridgeJars: Vector[java.net.URI], externalDependencyJars: Vector[String], scalacOptions: Vector[String], initialCommands: String, cleanupCommands: String): ConsoleConfig = new ConsoleConfig(scalaInstanceConfig, bridgeJars, externalDependencyJars, scalacOptions, initialCommands, cleanupCommands)
}
