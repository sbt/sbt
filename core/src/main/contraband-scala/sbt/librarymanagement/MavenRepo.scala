/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class MavenRepo private (
  name: String,
  root: String,
  localIfFile: Boolean) extends sbt.librarymanagement.MavenRepository(name, root, localIfFile) with Serializable {
  def isCache: Boolean = false
  private def this(name: String, root: String) = this(name, root, true)
  
  override def equals(o: Any): Boolean = o match {
    case x: MavenRepo => (this.name == x.name) && (this.root == x.root) && (this.localIfFile == x.localIfFile)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.MavenRepo".##) + name.##) + root.##) + localIfFile.##)
  }
  override def toString: String = {
    s"$name: $root"
  }
  protected[this] def copy(name: String = name, root: String = root, localIfFile: Boolean = localIfFile): MavenRepo = {
    new MavenRepo(name, root, localIfFile)
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
}
object MavenRepo {
  
  def apply(name: String, root: String): MavenRepo = new MavenRepo(name, root, true)
  def apply(name: String, root: String, localIfFile: Boolean): MavenRepo = new MavenRepo(name, root, localIfFile)
}
