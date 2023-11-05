/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.remotecache
final class PomRemoteCacheArtifact private (
  artifact: sbt.librarymanagement.Artifact,
  packaged: sbt.TaskKey[xsbti.VirtualFileRef]) extends sbt.internal.remotecache.RemoteCacheArtifact(artifact, packaged) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: PomRemoteCacheArtifact => (this.artifact == x.artifact) && (this.packaged == x.packaged)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.remotecache.PomRemoteCacheArtifact".##) + artifact.##) + packaged.##)
  }
  override def toString: String = {
    "PomRemoteCacheArtifact(" + artifact + ", " + packaged + ")"
  }
  private[this] def copy(artifact: sbt.librarymanagement.Artifact = artifact, packaged: sbt.TaskKey[xsbti.VirtualFileRef] = packaged): PomRemoteCacheArtifact = {
    new PomRemoteCacheArtifact(artifact, packaged)
  }
  def withArtifact(artifact: sbt.librarymanagement.Artifact): PomRemoteCacheArtifact = {
    copy(artifact = artifact)
  }
  def withPackaged(packaged: sbt.TaskKey[xsbti.VirtualFileRef]): PomRemoteCacheArtifact = {
    copy(packaged = packaged)
  }
}
object PomRemoteCacheArtifact {
  
  def apply(artifact: sbt.librarymanagement.Artifact, packaged: sbt.TaskKey[xsbti.VirtualFileRef]): PomRemoteCacheArtifact = new PomRemoteCacheArtifact(artifact, packaged)
}
