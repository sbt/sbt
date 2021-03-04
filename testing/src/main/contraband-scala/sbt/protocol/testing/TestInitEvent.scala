/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
/** Called once, at beginning of the testing. */
final class TestInitEvent private () extends sbt.protocol.testing.TestMessage() with Serializable {



override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
  case _: TestInitEvent => true
  case _ => false
})
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.testing.TestInitEvent".##)
}
override def toString: String = {
  "TestInitEvent()"
}
private[this] def copy(): TestInitEvent = {
  new TestInitEvent()
}

}
object TestInitEvent {
  
  def apply(): TestInitEvent = new TestInitEvent()
}
