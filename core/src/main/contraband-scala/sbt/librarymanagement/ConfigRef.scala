/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** A reference to Configuration. */
final class ConfigRef private (
  /** The name of the configuration that eventually get used by Maven. */
  val name: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ConfigRef => (this.name == x.name)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.librarymanagement.ConfigRef".##) + name.##)
  }
  override def toString: String = {
    name
  }
  private[this] def copy(name: String = name): ConfigRef = {
    new ConfigRef(name)
  }
  def withName(name: String): ConfigRef = {
    copy(name = name)
  }
}
object ConfigRef extends sbt.librarymanagement.ConfigRefFunctions {
  
  def apply(name: String): ConfigRef = new ConfigRef(name)
}
