/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
abstract class SettingQueryResponse() extends sbt.internal.protocol.EventMessage() with Serializable {




override def equals(o: Any): Boolean = o match {
  case x: SettingQueryResponse => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.internal.protocol.SettingQueryResponse".##)
}
override def toString: String = {
  "SettingQueryResponse()"
}
}
object SettingQueryResponse {
  
}
