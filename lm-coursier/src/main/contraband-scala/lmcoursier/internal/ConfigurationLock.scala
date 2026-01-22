/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class ConfigurationLock private (
  val name: String,
  val dependencies: Vector[lmcoursier.internal.DependencyLock]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ConfigurationLock => (this.name == x.name) && (this.dependencies == x.dependencies)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "lmcoursier.internal.ConfigurationLock".##) + name.##) + dependencies.##)
  }
  override def toString: String = {
    "ConfigurationLock(" + name + ", " + dependencies + ")"
  }
  private def copy(name: String = name, dependencies: Vector[lmcoursier.internal.DependencyLock] = dependencies): ConfigurationLock = {
    new ConfigurationLock(name, dependencies)
  }
  def withName(name: String): ConfigurationLock = {
    copy(name = name)
  }
  def withDependencies(dependencies: Vector[lmcoursier.internal.DependencyLock]): ConfigurationLock = {
    copy(dependencies = dependencies)
  }
}
object ConfigurationLock {
  
  def apply(name: String, dependencies: Vector[lmcoursier.internal.DependencyLock]): ConfigurationLock = new ConfigurationLock(name, dependencies)
}
