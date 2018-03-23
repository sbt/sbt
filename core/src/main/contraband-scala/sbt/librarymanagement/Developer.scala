/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class Developer private (
  val id: String,
  val name: String,
  val email: String,
  val url: java.net.URL) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Developer => (this.id == x.id) && (this.name == x.name) && (this.email == x.email) && (this.url == x.url)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.Developer".##) + id.##) + name.##) + email.##) + url.##)
  }
  override def toString: String = {
    "Developer(" + id + ", " + name + ", " + email + ", " + url + ")"
  }
  private[this] def copy(id: String = id, name: String = name, email: String = email, url: java.net.URL = url): Developer = {
    new Developer(id, name, email, url)
  }
  def withId(id: String): Developer = {
    copy(id = id)
  }
  def withName(name: String): Developer = {
    copy(name = name)
  }
  def withEmail(email: String): Developer = {
    copy(email = email)
  }
  def withUrl(url: java.net.URL): Developer = {
    copy(url = url)
  }
}
object Developer {
  
  def apply(id: String, name: String, email: String, url: java.net.URL): Developer = new Developer(id, name, email, url)
}
