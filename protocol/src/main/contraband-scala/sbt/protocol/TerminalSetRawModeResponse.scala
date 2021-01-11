/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetRawModeResponse private () extends sbt.protocol.EventMessage() with Serializable {



override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
  case _: TerminalSetRawModeResponse => true
  case _ => false
})
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.TerminalSetRawModeResponse".##)
}
override def toString: String = {
  "TerminalSetRawModeResponse()"
}
private[this] def copy(): TerminalSetRawModeResponse = {
  new TerminalSetRawModeResponse()
}

}
object TerminalSetRawModeResponse {
  
  def apply(): TerminalSetRawModeResponse = new TerminalSetRawModeResponse()
}
