/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalAttributesQuery private () extends sbt.protocol.CommandMessage() with Serializable {



override def equals(o: Any): Boolean = o match {
  case _: TerminalAttributesQuery => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.TerminalAttributesQuery".##)
}
override def toString: String = {
  "TerminalAttributesQuery()"
}
private[this] def copy(): TerminalAttributesQuery = {
  new TerminalAttributesQuery()
}

}
object TerminalAttributesQuery {
  
  def apply(): TerminalAttributesQuery = new TerminalAttributesQuery()
}
