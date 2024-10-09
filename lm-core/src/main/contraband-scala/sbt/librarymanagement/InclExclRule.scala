/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Defines a rule to either:
 * <ul>
 * <li>Exclude unwanted dependencies pulled in transitively by a module, or to</li>
 * <li>Include and merge artifacts coming from the ModuleDescriptor if "dependencyArtifacts" are also provided.</li>
 * </ul>
 * The use case that is applied depends on the parameter name which it is passed to, but the
 * filter has the same fields in both cases.
 */
final class InclExclRule private (
  val organization: String,
  val name: String,
  val artifact: String,
  val configurations: Vector[sbt.librarymanagement.ConfigRef],
  val crossVersion: sbt.librarymanagement.CrossVersion) extends Serializable {
  
  private def this() = this("*", "*", "*", Vector.empty, sbt.librarymanagement.Disabled())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: InclExclRule => (this.organization == x.organization) && (this.name == x.name) && (this.artifact == x.artifact) && (this.configurations == x.configurations) && (this.crossVersion == x.crossVersion)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.InclExclRule".##) + organization.##) + name.##) + artifact.##) + configurations.##) + crossVersion.##)
  }
  override def toString: String = {
    "InclExclRule(" + organization + ", " + name + ", " + artifact + ", " + configurations + ", " + crossVersion + ")"
  }
  private[this] def copy(organization: String = organization, name: String = name, artifact: String = artifact, configurations: Vector[sbt.librarymanagement.ConfigRef] = configurations, crossVersion: sbt.librarymanagement.CrossVersion = crossVersion): InclExclRule = {
    new InclExclRule(organization, name, artifact, configurations, crossVersion)
  }
  def withOrganization(organization: String): InclExclRule = {
    copy(organization = organization)
  }
  def withName(name: String): InclExclRule = {
    copy(name = name)
  }
  def withArtifact(artifact: String): InclExclRule = {
    copy(artifact = artifact)
  }
  def withConfigurations(configurations: Vector[sbt.librarymanagement.ConfigRef]): InclExclRule = {
    copy(configurations = configurations)
  }
  def withCrossVersion(crossVersion: sbt.librarymanagement.CrossVersion): InclExclRule = {
    copy(crossVersion = crossVersion)
  }
}
object InclExclRule extends sbt.librarymanagement.InclExclRuleFunctions {
  
  def apply(): InclExclRule = new InclExclRule()
  def apply(organization: String, name: String, artifact: String, configurations: Vector[sbt.librarymanagement.ConfigRef], crossVersion: sbt.librarymanagement.CrossVersion): InclExclRule = new InclExclRule(organization, name, artifact, configurations, crossVersion)
}
