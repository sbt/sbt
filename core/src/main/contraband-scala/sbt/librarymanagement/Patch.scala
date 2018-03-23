/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Cross-versions a module by stripping off -bin-suffix.
 * This is intended for patch-version compatible alternative replacements.
 */
final class Patch private () extends sbt.librarymanagement.CrossVersion() with Serializable {



override def equals(o: Any): Boolean = o match {
  case _: Patch => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.librarymanagement.Patch".##)
}
override def toString: String = {
  "Patch()"
}
private[this] def copy(): Patch = {
  new Patch()
}

}
object Patch {
  
  def apply(): Patch = new Patch()
}
