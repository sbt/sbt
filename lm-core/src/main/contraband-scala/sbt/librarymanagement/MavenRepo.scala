/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** This is the internal implementation of actual Maven Repository (as opposed to a file cache). */
final class MavenRepo private (
  name: String,
  root: String,
  localIfFile: Boolean,
  val _allowInsecureProtocol: Boolean) extends sbt.librarymanagement.MavenRepository(name, root, localIfFile) with Serializable {
  override def isCache: Boolean = false
  override def allowInsecureProtocol: Boolean = _allowInsecureProtocol
  private[sbt] override def validateProtocol(logger: sbt.util.Logger): Boolean = Resolver.validateMavenRepo(this, logger)
  private def this(name: String, root: String) = this(name, root, true, false)
  private def this(name: String, root: String, localIfFile: Boolean) = this(name, root, localIfFile, false)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: MavenRepo => (this.name == x.name) && (this.root == x.root) && (this.localIfFile == x.localIfFile) && (this._allowInsecureProtocol == x._allowInsecureProtocol)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.MavenRepo".##) + name.##) + root.##) + localIfFile.##) + _allowInsecureProtocol.##)
  }
  override def toString: String = {
    s"$name: $root"
  }
  private[this] def copy(name: String = name, root: String = root, localIfFile: Boolean = localIfFile, _allowInsecureProtocol: Boolean = _allowInsecureProtocol): MavenRepo = {
    new MavenRepo(name, root, localIfFile, _allowInsecureProtocol)
  }
  def withName(name: String): MavenRepo = {
    copy(name = name)
  }
  def withRoot(root: String): MavenRepo = {
    copy(root = root)
  }
  def withLocalIfFile(localIfFile: Boolean): MavenRepo = {
    copy(localIfFile = localIfFile)
  }
  def with_allowInsecureProtocol(_allowInsecureProtocol: Boolean): MavenRepo = {
    copy(_allowInsecureProtocol = _allowInsecureProtocol)
  }
}
object MavenRepo {
  
  def apply(name: String, root: String): MavenRepo = new MavenRepo(name, root)
  def apply(name: String, root: String, localIfFile: Boolean): MavenRepo = new MavenRepo(name, root, localIfFile)
  def apply(name: String, root: String, localIfFile: Boolean, _allowInsecureProtocol: Boolean): MavenRepo = new MavenRepo(name, root, localIfFile, _allowInsecureProtocol)
}
