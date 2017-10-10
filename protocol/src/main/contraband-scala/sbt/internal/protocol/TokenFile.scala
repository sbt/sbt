/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol
final class TokenFile private (
  val uri: String,
  val token: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TokenFile => (this.uri == x.uri) && (this.token == x.token)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.protocol.TokenFile".##) + uri.##) + token.##)
  }
  override def toString: String = {
    "TokenFile(" + uri + ", " + token + ")"
  }
  protected[this] def copy(uri: String = uri, token: String = token): TokenFile = {
    new TokenFile(uri, token)
  }
  def withUri(uri: String): TokenFile = {
    copy(uri = uri)
  }
  def withToken(token: String): TokenFile = {
    copy(token = token)
  }
}
object TokenFile {
  
  def apply(uri: String, token: String): TokenFile = new TokenFile(uri, token)
}
