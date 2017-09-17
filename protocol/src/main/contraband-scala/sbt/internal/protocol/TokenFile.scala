/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
final class TokenFile private (
  val url: String,
  val token: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TokenFile => (this.url == x.url) && (this.token == x.token)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.protocol.TokenFile".##) + url.##) + token.##)
  }
  override def toString: String = {
    "TokenFile(" + url + ", " + token + ")"
  }
  protected[this] def copy(url: String = url, token: String = token): TokenFile = {
    new TokenFile(url, token)
  }
  def withUrl(url: String): TokenFile = {
    copy(url = url)
  }
  def withToken(token: String): TokenFile = {
    copy(token = token)
  }
}
object TokenFile {
  
  def apply(url: String, token: String): TokenFile = new TokenFile(url, token)
}
