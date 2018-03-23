/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Disables cross versioning for a module. */
final class Disabled private () extends sbt.librarymanagement.CrossVersion() with Serializable {



override def equals(o: Any): Boolean = o match {
  case _: Disabled => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.librarymanagement.Disabled".##)
}
override def toString: String = {
  "Disabled()"
}
private[this] def copy(): Disabled = {
  new Disabled()
}

}
object Disabled {
  
  def apply(): Disabled = new Disabled()
}
