/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Message to invoke command. */
abstract class CommandMessage() extends Serializable {




override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
  case _: CommandMessage => true
  case _ => false
})
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.CommandMessage".##)
}
override def toString: String = {
  "CommandMessage()"
}
}
object CommandMessage {
  
}
