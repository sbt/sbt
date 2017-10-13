/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class ClientCapabilities private () extends Serializable {



override def equals(o: Any): Boolean = o match {
  case x: ClientCapabilities => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.internal.langserver.ClientCapabilities".##)
}
override def toString: String = {
  "ClientCapabilities()"
}
protected[this] def copy(): ClientCapabilities = {
  new ClientCapabilities()
}

}
object ClientCapabilities {
  
  def apply(): ClientCapabilities = new ClientCapabilities()
}
