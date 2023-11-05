/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.remotecache
final class CompileRemoteCacheArtifact private (
  artifact: sbt.librarymanagement.Artifact,
  packaged: sbt.TaskKey[xsbti.VirtualFileRef],
  val extractDirectory: java.io.File,
  val analysisFile: java.io.File) extends sbt.internal.remotecache.RemoteCacheArtifact(artifact, packaged) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CompileRemoteCacheArtifact => (this.artifact == x.artifact) && (this.packaged == x.packaged) && (this.extractDirectory == x.extractDirectory) && (this.analysisFile == x.analysisFile)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.remotecache.CompileRemoteCacheArtifact".##) + artifact.##) + packaged.##) + extractDirectory.##) + analysisFile.##)
  }
  override def toString: String = {
    "CompileRemoteCacheArtifact(" + artifact + ", " + packaged + ", " + extractDirectory + ", " + analysisFile + ")"
  }
  private[this] def copy(artifact: sbt.librarymanagement.Artifact = artifact, packaged: sbt.TaskKey[xsbti.VirtualFileRef] = packaged, extractDirectory: java.io.File = extractDirectory, analysisFile: java.io.File = analysisFile): CompileRemoteCacheArtifact = {
    new CompileRemoteCacheArtifact(artifact, packaged, extractDirectory, analysisFile)
  }
  def withArtifact(artifact: sbt.librarymanagement.Artifact): CompileRemoteCacheArtifact = {
    copy(artifact = artifact)
  }
  def withPackaged(packaged: sbt.TaskKey[xsbti.VirtualFileRef]): CompileRemoteCacheArtifact = {
    copy(packaged = packaged)
  }
  def withExtractDirectory(extractDirectory: java.io.File): CompileRemoteCacheArtifact = {
    copy(extractDirectory = extractDirectory)
  }
  def withAnalysisFile(analysisFile: java.io.File): CompileRemoteCacheArtifact = {
    copy(analysisFile = analysisFile)
  }
}
object CompileRemoteCacheArtifact {
  
  def apply(artifact: sbt.librarymanagement.Artifact, packaged: sbt.TaskKey[xsbti.VirtualFileRef], extractDirectory: java.io.File, analysisFile: java.io.File): CompileRemoteCacheArtifact = new CompileRemoteCacheArtifact(artifact, packaged, extractDirectory, analysisFile)
}
