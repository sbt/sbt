/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
/** Called for each class or equivalent grouping. */
final class StartTestGroupEvent private (
  val name: String) extends sbt.protocol.testing.TestMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: StartTestGroupEvent => (this.name == x.name)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.testing.StartTestGroupEvent".##) + name.##)
  }
  override def toString: String = {
    "StartTestGroupEvent(" + name + ")"
  }
  protected[this] def copy(name: String = name): StartTestGroupEvent = {
    new StartTestGroupEvent(name)
  }
  def withName(name: String): StartTestGroupEvent = {
    copy(name = name)
  }
}
object StartTestGroupEvent {
  
  def apply(name: String): StartTestGroupEvent = new StartTestGroupEvent(name)
}
