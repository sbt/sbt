/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetEchoResponse private () extends sbt.protocol.EventMessage() with Serializable {



override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
  case _: TerminalSetEchoResponse => true
  case _ => false
})
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.TerminalSetEchoResponse".##)
}
override def toString: String = {
  "TerminalSetEchoResponse()"
}
private def copy(): TerminalSetEchoResponse = {
  new TerminalSetEchoResponse()
}

}
object TerminalSetEchoResponse {
  
  def apply(): TerminalSetEchoResponse = new TerminalSetEchoResponse()
}
