/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
final class Info private (
  val description: String,
  val homePage: String,
  val licenses: Seq[(String, Option[String])],
  val developers: Seq[Developer],
  val publication: Option[DateTime]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Info => (this.description == x.description) && (this.homePage == x.homePage) && (this.licenses == x.licenses) && (this.developers == x.developers) && (this.publication == x.publication)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.Info".##) + description.##) + homePage.##) + licenses.##) + developers.##) + publication.##)
  }
  override def toString: String = {
    "Info(" + description + ", " + homePage + ", " + licenses + ", " + developers + ", " + publication + ")"
  }
  private[this] def copy(description: String = description, homePage: String = homePage, licenses: Seq[(String, Option[String])] = licenses, developers: Seq[Developer] = developers, publication: Option[DateTime] = publication): Info = {
    new Info(description, homePage, licenses, developers, publication)
  }
  def withDescription(description: String): Info = {
    copy(description = description)
  }
  def withHomePage(homePage: String): Info = {
    copy(homePage = homePage)
  }
  def withLicenses(licenses: Seq[(String, Option[String])]): Info = {
    copy(licenses = licenses)
  }
  def withDevelopers(developers: Seq[Developer]): Info = {
    copy(developers = developers)
  }
  def withPublication(publication: Option[DateTime]): Info = {
    copy(publication = publication)
  }
}
object Info {
  
  def apply(description: String, homePage: String, licenses: Seq[(String, Option[String])], developers: Seq[Developer], publication: Option[DateTime]): Info = new Info(description, homePage, licenses, developers, publication)
}
