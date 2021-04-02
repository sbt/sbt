/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetSizeResponse private () extends sbt.protocol.EventMessage() with Serializable {



override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
  case _: TerminalSetSizeResponse => true
  case _ => false
})
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.TerminalSetSizeResponse".##)
}
override def toString: String = {
  "TerminalSetSizeResponse()"
}
private[this] def copy(): TerminalSetSizeResponse = {
  new TerminalSetSizeResponse()
}

}
object TerminalSetSizeResponse {
  
  def apply(): TerminalSetSizeResponse = new TerminalSetSizeResponse()
}
