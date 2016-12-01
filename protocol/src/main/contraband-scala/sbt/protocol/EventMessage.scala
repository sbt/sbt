/**
 * This code is generated using sbt-datatype.
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
  17
}
override def toString: String = {
  "EventMessage()"
}
}
object EventMessage {
  
}
