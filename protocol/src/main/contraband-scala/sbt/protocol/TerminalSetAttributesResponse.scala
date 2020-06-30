/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetAttributesResponse private () extends sbt.protocol.EventMessage() with Serializable {



override def equals(o: Any): Boolean = o match {
  case _: TerminalSetAttributesResponse => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.TerminalSetAttributesResponse".##)
}
override def toString: String = {
  "TerminalSetAttributesResponse()"
}
private[this] def copy(): TerminalSetAttributesResponse = {
  new TerminalSetAttributesResponse()
}

}
object TerminalSetAttributesResponse {
  
  def apply(): TerminalSetAttributesResponse = new TerminalSetAttributesResponse()
}
