/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Message for events. */
abstract class EventMessage() extends Serializable {




override def equals(o: Any): Boolean = o match {
  case x: EventMessage => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.EventMessage".##)
}
override def toString: String = {
  "EventMessage()"
}
}
object EventMessage {
  
}
