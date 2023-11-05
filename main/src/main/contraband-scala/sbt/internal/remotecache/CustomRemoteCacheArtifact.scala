/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.remotecache
final class CustomRemoteCacheArtifact private (
  artifact: sbt.librarymanagement.Artifact,
  packaged: sbt.TaskKey[xsbti.VirtualFileRef],
  val extractDirectory: java.io.File,
  val preserveLastModified: Boolean) extends sbt.internal.remotecache.RemoteCacheArtifact(artifact, packaged) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CustomRemoteCacheArtifact => (this.artifact == x.artifact) && (this.packaged == x.packaged) && (this.extractDirectory == x.extractDirectory) && (this.preserveLastModified == x.preserveLastModified)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.remotecache.CustomRemoteCacheArtifact".##) + artifact.##) + packaged.##) + extractDirectory.##) + preserveLastModified.##)
  }
  override def toString: String = {
    "CustomRemoteCacheArtifact(" + artifact + ", " + packaged + ", " + extractDirectory + ", " + preserveLastModified + ")"
  }
  private[this] def copy(artifact: sbt.librarymanagement.Artifact = artifact, packaged: sbt.TaskKey[xsbti.VirtualFileRef] = packaged, extractDirectory: java.io.File = extractDirectory, preserveLastModified: Boolean = preserveLastModified): CustomRemoteCacheArtifact = {
    new CustomRemoteCacheArtifact(artifact, packaged, extractDirectory, preserveLastModified)
  }
  def withArtifact(artifact: sbt.librarymanagement.Artifact): CustomRemoteCacheArtifact = {
    copy(artifact = artifact)
  }
  def withPackaged(packaged: sbt.TaskKey[xsbti.VirtualFileRef]): CustomRemoteCacheArtifact = {
    copy(packaged = packaged)
  }
  def withExtractDirectory(extractDirectory: java.io.File): CustomRemoteCacheArtifact = {
    copy(extractDirectory = extractDirectory)
  }
  def withPreserveLastModified(preserveLastModified: Boolean): CustomRemoteCacheArtifact = {
    copy(preserveLastModified = preserveLastModified)
  }
}
object CustomRemoteCacheArtifact {
  
  def apply(artifact: sbt.librarymanagement.Artifact, packaged: sbt.TaskKey[xsbti.VirtualFileRef], extractDirectory: java.io.File, preserveLastModified: Boolean): CustomRemoteCacheArtifact = new CustomRemoteCacheArtifact(artifact, packaged, extractDirectory, preserveLastModified)
}
