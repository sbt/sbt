/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Additional information about a project module */
final class ModuleInfo private (
  val nameFormal: String,
  val description: String,
  val homepage: Option[java.net.URL],
  val startYear: Option[Int],
  val licenses: Vector[scala.Tuple2[String, java.net.URL]],
  val organizationName: String,
  val organizationHomepage: Option[java.net.URL],
  val scmInfo: Option[sbt.librarymanagement.ScmInfo],
  val developers: Vector[sbt.librarymanagement.Developer]) extends Serializable {
  
  private def this(nameFormal: String) = this(nameFormal, "", None, None, Vector.empty, "", None, None, Vector.empty)
  
  override def equals(o: Any): Boolean = o match {
    case x: ModuleInfo => (this.nameFormal == x.nameFormal) && (this.description == x.description) && (this.homepage == x.homepage) && (this.startYear == x.startYear) && (this.licenses == x.licenses) && (this.organizationName == x.organizationName) && (this.organizationHomepage == x.organizationHomepage) && (this.scmInfo == x.scmInfo) && (this.developers == x.developers)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ModuleInfo".##) + nameFormal.##) + description.##) + homepage.##) + startYear.##) + licenses.##) + organizationName.##) + organizationHomepage.##) + scmInfo.##) + developers.##)
  }
  override def toString: String = {
    "ModuleInfo(" + nameFormal + ", " + description + ", " + homepage + ", " + startYear + ", " + licenses + ", " + organizationName + ", " + organizationHomepage + ", " + scmInfo + ", " + developers + ")"
  }
  protected[this] def copy(nameFormal: String = nameFormal, description: String = description, homepage: Option[java.net.URL] = homepage, startYear: Option[Int] = startYear, licenses: Vector[scala.Tuple2[String, java.net.URL]] = licenses, organizationName: String = organizationName, organizationHomepage: Option[java.net.URL] = organizationHomepage, scmInfo: Option[sbt.librarymanagement.ScmInfo] = scmInfo, developers: Vector[sbt.librarymanagement.Developer] = developers): ModuleInfo = {
    new ModuleInfo(nameFormal, description, homepage, startYear, licenses, organizationName, organizationHomepage, scmInfo, developers)
  }
  def withNameFormal(nameFormal: String): ModuleInfo = {
    copy(nameFormal = nameFormal)
  }
  def withDescription(description: String): ModuleInfo = {
    copy(description = description)
  }
  def withHomepage(homepage: Option[java.net.URL]): ModuleInfo = {
    copy(homepage = homepage)
  }
  def withStartYear(startYear: Option[Int]): ModuleInfo = {
    copy(startYear = startYear)
  }
  def withLicenses(licenses: Vector[scala.Tuple2[String, java.net.URL]]): ModuleInfo = {
    copy(licenses = licenses)
  }
  def withOrganizationName(organizationName: String): ModuleInfo = {
    copy(organizationName = organizationName)
  }
  def withOrganizationHomepage(organizationHomepage: Option[java.net.URL]): ModuleInfo = {
    copy(organizationHomepage = organizationHomepage)
  }
  def withScmInfo(scmInfo: Option[sbt.librarymanagement.ScmInfo]): ModuleInfo = {
    copy(scmInfo = scmInfo)
  }
  def withDevelopers(developers: Vector[sbt.librarymanagement.Developer]): ModuleInfo = {
    copy(developers = developers)
  }
}
object ModuleInfo {
  
  def apply(nameFormal: String): ModuleInfo = new ModuleInfo(nameFormal)
  def apply(nameFormal: String, description: String, homepage: Option[java.net.URL], startYear: Option[Int], licenses: Vector[scala.Tuple2[String, java.net.URL]], organizationName: String, organizationHomepage: Option[java.net.URL], scmInfo: Option[sbt.librarymanagement.ScmInfo], developers: Vector[sbt.librarymanagement.Developer]): ModuleInfo = new ModuleInfo(nameFormal, description, homepage, startYear, licenses, organizationName, organizationHomepage, scmInfo, developers)
}
