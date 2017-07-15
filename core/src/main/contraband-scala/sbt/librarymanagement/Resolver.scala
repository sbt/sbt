/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
abstract class Resolver(
  val name: String) extends Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Resolver => (this.name == x.name)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.librarymanagement.Resolver".##) + name.##)
  }
  override def toString: String = {
    "Resolver(" + name + ")"
  }
}
object Resolver extends sbt.librarymanagement.ResolverFunctions {
  
}
