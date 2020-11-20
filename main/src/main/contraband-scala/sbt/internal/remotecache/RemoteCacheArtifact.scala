/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.remotecache
abstract class RemoteCacheArtifact(
  val artifact: sbt.librarymanagement.Artifact,
  val packaged: sbt.TaskKey[java.io.File]) extends Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: RemoteCacheArtifact => (this.artifact == x.artifact) && (this.packaged == x.packaged)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.remotecache.RemoteCacheArtifact".##) + artifact.##) + packaged.##)
  }
  override def toString: String = {
    "RemoteCacheArtifact(" + artifact + ", " + packaged + ")"
  }
}
object RemoteCacheArtifact {
  
}
