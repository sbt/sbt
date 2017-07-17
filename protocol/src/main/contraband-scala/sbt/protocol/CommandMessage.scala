/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Message to invoke command. */
abstract class CommandMessage() extends Serializable {




override def equals(o: Any): Boolean = o match {
  case x: CommandMessage => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.CommandMessage".##)
}
override def toString: String = {
  "CommandMessage()"
}
}
object CommandMessage {
  
}
