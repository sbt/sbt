/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class ConsoleConfig private (
  val scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig,
  val bridgeJar: String,
  val externalDependencyJars: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ConsoleConfig => (this.scalaInstanceConfig == x.scalaInstanceConfig) && (this.bridgeJar == x.bridgeJar) && (this.externalDependencyJars == x.externalDependencyJars)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.ConsoleConfig".##) + scalaInstanceConfig.##) + bridgeJar.##) + externalDependencyJars.##)
  }
  override def toString: String = {
    "ConsoleConfig(" + scalaInstanceConfig + ", " + bridgeJar + ", " + externalDependencyJars + ")"
  }
  private def copy(scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig = scalaInstanceConfig, bridgeJar: String = bridgeJar, externalDependencyJars: Vector[String] = externalDependencyJars): ConsoleConfig = {
    new ConsoleConfig(scalaInstanceConfig, bridgeJar, externalDependencyJars)
  }
  def withScalaInstanceConfig(scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig): ConsoleConfig = {
    copy(scalaInstanceConfig = scalaInstanceConfig)
  }
  def withBridgeJar(bridgeJar: String): ConsoleConfig = {
    copy(bridgeJar = bridgeJar)
  }
  def withExternalDependencyJars(externalDependencyJars: Vector[String]): ConsoleConfig = {
    copy(externalDependencyJars = externalDependencyJars)
  }
}
object ConsoleConfig {
  
  def apply(scalaInstanceConfig: sbt.internal.worker.ScalaInstanceConfig, bridgeJar: String, externalDependencyJars: Vector[String]): ConsoleConfig = new ConsoleConfig(scalaInstanceConfig, bridgeJar, externalDependencyJars)
}
