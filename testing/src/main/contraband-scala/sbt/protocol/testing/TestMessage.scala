/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
/** Events for testing */
abstract class TestMessage() extends Serializable {




override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
  case _: TestMessage => true
  case _ => false
})
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.testing.TestMessage".##)
}
override def toString: String = {
  "TestMessage()"
}
}
object TestMessage {
  
}
