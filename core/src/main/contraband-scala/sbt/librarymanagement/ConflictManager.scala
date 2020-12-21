/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** See http://ant.apache.org/ivy/history/latest-milestone/settings/conflict-managers.html for details of the different conflict managers. */
final class ConflictManager private (
  val name: String,
  val organization: String,
  val module: String) extends Serializable {
  
  private def this(name: String) = this(name, "*", "*")
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ConflictManager => (this.name == x.name) && (this.organization == x.organization) && (this.module == x.module)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ConflictManager".##) + name.##) + organization.##) + module.##)
  }
  override def toString: String = {
    "ConflictManager(" + name + ", " + organization + ", " + module + ")"
  }
  private[this] def copy(name: String = name, organization: String = organization, module: String = module): ConflictManager = {
    new ConflictManager(name, organization, module)
  }
  def withName(name: String): ConflictManager = {
    copy(name = name)
  }
  def withOrganization(organization: String): ConflictManager = {
    copy(organization = organization)
  }
  def withModule(module: String): ConflictManager = {
    copy(module = module)
  }
}
object ConflictManager extends sbt.librarymanagement.ConflictManagerFunctions {
  
  def apply(name: String): ConflictManager = new ConflictManager(name)
  def apply(name: String, organization: String, module: String): ConflictManager = new ConflictManager(name, organization, module)
}
