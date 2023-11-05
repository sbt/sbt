/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.remotecache
final class TestRemoteCacheArtifact private (
  artifact: sbt.librarymanagement.Artifact,
  packaged: sbt.TaskKey[xsbti.VirtualFileRef],
  val extractDirectory: java.io.File,
  val analysisFile: java.io.File,
  val testResult: java.io.File) extends sbt.internal.remotecache.RemoteCacheArtifact(artifact, packaged) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TestRemoteCacheArtifact => (this.artifact == x.artifact) && (this.packaged == x.packaged) && (this.extractDirectory == x.extractDirectory) && (this.analysisFile == x.analysisFile) && (this.testResult == x.testResult)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.remotecache.TestRemoteCacheArtifact".##) + artifact.##) + packaged.##) + extractDirectory.##) + analysisFile.##) + testResult.##)
  }
  override def toString: String = {
    "TestRemoteCacheArtifact(" + artifact + ", " + packaged + ", " + extractDirectory + ", " + analysisFile + ", " + testResult + ")"
  }
  private[this] def copy(artifact: sbt.librarymanagement.Artifact = artifact, packaged: sbt.TaskKey[xsbti.VirtualFileRef] = packaged, extractDirectory: java.io.File = extractDirectory, analysisFile: java.io.File = analysisFile, testResult: java.io.File = testResult): TestRemoteCacheArtifact = {
    new TestRemoteCacheArtifact(artifact, packaged, extractDirectory, analysisFile, testResult)
  }
  def withArtifact(artifact: sbt.librarymanagement.Artifact): TestRemoteCacheArtifact = {
    copy(artifact = artifact)
  }
  def withPackaged(packaged: sbt.TaskKey[xsbti.VirtualFileRef]): TestRemoteCacheArtifact = {
    copy(packaged = packaged)
  }
  def withExtractDirectory(extractDirectory: java.io.File): TestRemoteCacheArtifact = {
    copy(extractDirectory = extractDirectory)
  }
  def withAnalysisFile(analysisFile: java.io.File): TestRemoteCacheArtifact = {
    copy(analysisFile = analysisFile)
  }
  def withTestResult(testResult: java.io.File): TestRemoteCacheArtifact = {
    copy(testResult = testResult)
  }
}
object TestRemoteCacheArtifact {
  
  def apply(artifact: sbt.librarymanagement.Artifact, packaged: sbt.TaskKey[xsbti.VirtualFileRef], extractDirectory: java.io.File, analysisFile: java.io.File, testResult: java.io.File): TestRemoteCacheArtifact = new TestRemoteCacheArtifact(artifact, packaged, extractDirectory, analysisFile, testResult)
}
