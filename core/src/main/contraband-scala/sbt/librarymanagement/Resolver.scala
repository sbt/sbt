/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
abstract class Resolver(
  val name: String) extends Serializable {
  /** check for HTTP */
  private[sbt] def validateProtocol(logger: sbt.util.Logger): Boolean = false
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: Resolver => (this.name == x.name)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.librarymanagement.Resolver".##) + name.##)
  }
  override def toString: String = {
    "Resolver(" + name + ")"
  }
}
object Resolver extends sbt.librarymanagement.ResolverFunctions {
  
}
