/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
final class Developer private (
  val id: String,
  val name: String,
  val url: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Developer => (this.id == x.id) && (this.name == x.name) && (this.url == x.url)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.Developer".##) + id.##) + name.##) + url.##)
  }
  override def toString: String = {
    "Developer(" + id + ", " + name + ", " + url + ")"
  }
  private[this] def copy(id: String = id, name: String = name, url: String = url): Developer = {
    new Developer(id, name, url)
  }
  def withId(id: String): Developer = {
    copy(id = id)
  }
  def withName(name: String): Developer = {
    copy(name = name)
  }
  def withUrl(url: String): Developer = {
    copy(url = url)
  }
}
object Developer {
  
  def apply(id: String, name: String, url: String): Developer = new Developer(id, name, url)
}
