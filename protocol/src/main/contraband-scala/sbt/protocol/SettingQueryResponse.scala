/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
abstract class SettingQueryResponse() extends sbt.protocol.EventMessage() with Serializable {




override def equals(o: Any): Boolean = o match {
  case x: SettingQueryResponse => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.protocol.SettingQueryResponse".##)
}
override def toString: String = {
  "SettingQueryResponse()"
}
}
object SettingQueryResponse {
  
}
