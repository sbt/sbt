/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class ChainedResolver private (
  name: String,
  val resolvers: Vector[sbt.librarymanagement.Resolver]) extends sbt.librarymanagement.Resolver(name) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ChainedResolver => (this.name == x.name) && (this.resolvers == x.resolvers)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.ChainedResolver".##) + name.##) + resolvers.##)
  }
  override def toString: String = {
    "ChainedResolver(" + name + ", " + resolvers + ")"
  }
  private[this] def copy(name: String = name, resolvers: Vector[sbt.librarymanagement.Resolver] = resolvers): ChainedResolver = {
    new ChainedResolver(name, resolvers)
  }
  def withName(name: String): ChainedResolver = {
    copy(name = name)
  }
  def withResolvers(resolvers: Vector[sbt.librarymanagement.Resolver]): ChainedResolver = {
    copy(resolvers = resolvers)
  }
}
object ChainedResolver {
  
  def apply(name: String, resolvers: Vector[sbt.librarymanagement.Resolver]): ChainedResolver = new ChainedResolver(name, resolvers)
}
