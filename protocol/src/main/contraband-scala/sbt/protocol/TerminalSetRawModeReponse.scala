/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetRawModeReponse private () extends sbt.protocol.EventMessage() with Serializable {



override def equals(o: Any): Boolean = o match {
  case _: TerminalSetRawModeReponse => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.TerminalSetRawModeReponse".##)
}
override def toString: String = {
  "TerminalSetRawModeReponse()"
}
private[this] def copy(): TerminalSetRawModeReponse = {
  new TerminalSetRawModeReponse()
}

}
object TerminalSetRawModeReponse {
  
  def apply(): TerminalSetRawModeReponse = new TerminalSetRawModeReponse()
}
