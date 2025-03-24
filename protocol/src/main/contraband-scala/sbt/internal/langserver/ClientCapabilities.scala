/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class ClientCapabilities private () extends Serializable {



override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
  case _: ClientCapabilities => true
  case _ => false
})
override def hashCode: Int = {
  37 * (17 + "sbt.internal.langserver.ClientCapabilities".##)
}
override def toString: String = {
  "ClientCapabilities()"
}
private def copy(): ClientCapabilities = {
  new ClientCapabilities()
}

}
object ClientCapabilities {
  
  def apply(): ClientCapabilities = new ClientCapabilities()
}
