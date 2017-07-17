/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
/** Called once, at beginning of the testing. */
final class TestInitEvent private () extends sbt.protocol.testing.TestMessage() with Serializable {



override def equals(o: Any): Boolean = o match {
  case x: TestInitEvent => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.testing.TestInitEvent".##)
}
override def toString: String = {
  "TestInitEvent()"
}
protected[this] def copy(): TestInitEvent = {
  new TestInitEvent()
}

}
object TestInitEvent {
  
  def apply(): TestInitEvent = new TestInitEvent()
}
