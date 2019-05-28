/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier
final class FallbackDependency private (
  val module: lmcoursier.definitions.Module,
  val version: String,
  val url: java.net.URL,
  val changing: Boolean) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: FallbackDependency => (this.module == x.module) && (this.version == x.version) && (this.url == x.url) && (this.changing == x.changing)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.FallbackDependency".##) + module.##) + version.##) + url.##) + changing.##)
  }
  override def toString: String = {
    "FallbackDependency(" + module + ", " + version + ", " + url + ", " + changing + ")"
  }
  private[this] def copy(module: lmcoursier.definitions.Module = module, version: String = version, url: java.net.URL = url, changing: Boolean = changing): FallbackDependency = {
    new FallbackDependency(module, version, url, changing)
  }
  def withModule(module: lmcoursier.definitions.Module): FallbackDependency = {
    copy(module = module)
  }
  def withVersion(version: String): FallbackDependency = {
    copy(version = version)
  }
  def withUrl(url: java.net.URL): FallbackDependency = {
    copy(url = url)
  }
  def withChanging(changing: Boolean): FallbackDependency = {
    copy(changing = changing)
  }
}
object FallbackDependency {
  
  def apply(module: lmcoursier.definitions.Module, version: String, url: java.net.URL, changing: Boolean): FallbackDependency = new FallbackDependency(module, version, url, changing)
}
