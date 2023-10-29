/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy
final class IvyPaths private (
  val baseDirectory: String,
  val ivyHome: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: IvyPaths => (this.baseDirectory == x.baseDirectory) && (this.ivyHome == x.ivyHome)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.ivy.IvyPaths".##) + baseDirectory.##) + ivyHome.##)
  }
  override def toString: String = {
    "IvyPaths(" + baseDirectory + ", " + ivyHome + ")"
  }
  private[this] def copy(baseDirectory: String = baseDirectory, ivyHome: Option[String] = ivyHome): IvyPaths = {
    new IvyPaths(baseDirectory, ivyHome)
  }
  def withBaseDirectory(baseDirectory: String): IvyPaths = {
    copy(baseDirectory = baseDirectory)
  }
  def withIvyHome(ivyHome: Option[String]): IvyPaths = {
    copy(ivyHome = ivyHome)
  }
  def withIvyHome(ivyHome: String): IvyPaths = {
    copy(ivyHome = Option(ivyHome))
  }
}
object IvyPaths {
  
  def apply(baseDirectory: String, ivyHome: Option[String]): IvyPaths = new IvyPaths(baseDirectory, ivyHome)
  def apply(baseDirectory: String, ivyHome: String): IvyPaths = new IvyPaths(baseDirectory, Option(ivyHome))
}
