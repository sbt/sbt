/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
/** Events for testing */
abstract class TestMessage() extends Serializable {




override def equals(o: Any): Boolean = o match {
  case x: TestMessage => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.testing.TestMessage".##)
}
override def toString: String = {
  "TestMessage()"
}
}
object TestMessage {
  
}
