/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalGetSizeQuery private () extends sbt.protocol.CommandMessage() with Serializable {



override def equals(o: Any): Boolean = o match {
  case _: TerminalGetSizeQuery => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.TerminalGetSizeQuery".##)
}
override def toString: String = {
  "TerminalGetSizeQuery()"
}
private[this] def copy(): TerminalGetSizeQuery = {
  new TerminalGetSizeQuery()
}

}
object TerminalGetSizeQuery {
  
  def apply(): TerminalGetSizeQuery = new TerminalGetSizeQuery()
}
