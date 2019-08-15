package lmcoursier.credentials

final class FileCredentials private(
  val path: String,
  val optional: Boolean
) extends Credentials {

  private def this(path: String) = this(path, true)

  override def equals(o: Any): Boolean = o match {
    case x: FileCredentials => (this.path == x.path) && (this.optional == x.optional)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "lmcoursier.credentials.CredentialFile".##) + path.##) + optional.##)
  }
  override def toString: String = {
    "CredentialFile(" + path + ", " + optional + ")"
  }
  private[this] def copy(path: String = path, optional: Boolean = optional): FileCredentials = {
    new FileCredentials(path, optional)
  }
  def withPath(path: String): FileCredentials = {
    copy(path = path)
  }
  def withOptional(optional: Boolean): FileCredentials = {
    copy(optional = optional)
  }
}
object FileCredentials {

  def apply(path: String): FileCredentials = new FileCredentials(path)
  def apply(path: String, optional: Boolean): FileCredentials = new FileCredentials(path, optional)
}
