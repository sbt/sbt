/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * An instance of maven CACHE directory.  You cannot treat a cache directory the same as a a remote repository because
 * the metadata is different (see Aether ML discussion).
 */
final class MavenCache private (
  name: String,
  root: String,
  localIfFile: Boolean,
  val rootFile: java.io.File) extends sbt.librarymanagement.MavenRepository(name, root, localIfFile) with Serializable {
  def this(name: String, rootFile: java.io.File) = this(name, rootFile.toURI.toURL.toString, true, rootFile)
  override def isCache: Boolean = true
  override def allowInsecureProtocol: Boolean = false
  private def this(name: String, root: String, rootFile: java.io.File) = this(name, root, true, rootFile)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: MavenCache => (this.name == x.name) && (this.root == x.root) && (this.localIfFile == x.localIfFile) && (this.rootFile == x.rootFile)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.MavenCache".##) + name.##) + root.##) + localIfFile.##) + rootFile.##)
  }
  override def toString: String = {
    s"cache:$name: ${rootFile.getAbsolutePath}"
  }
  private[this] def copy(name: String = name, root: String = root, localIfFile: Boolean = localIfFile, rootFile: java.io.File = rootFile): MavenCache = {
    new MavenCache(name, root, localIfFile, rootFile)
  }
  def withName(name: String): MavenCache = {
    copy(name = name)
  }
  def withRoot(root: String): MavenCache = {
    copy(root = root)
  }
  def withLocalIfFile(localIfFile: Boolean): MavenCache = {
    copy(localIfFile = localIfFile)
  }
  def withRootFile(rootFile: java.io.File): MavenCache = {
    copy(rootFile = rootFile)
  }
}
object MavenCache {
  def apply(name: String, rootFile: java.io.File): MavenCache = new MavenCache(name, rootFile)
  def apply(name: String, root: String, rootFile: java.io.File): MavenCache = new MavenCache(name, root, rootFile)
  def apply(name: String, root: String, localIfFile: Boolean, rootFile: java.io.File): MavenCache = new MavenCache(name, root, localIfFile, rootFile)
}
