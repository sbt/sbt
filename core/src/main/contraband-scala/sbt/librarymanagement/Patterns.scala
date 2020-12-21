/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class Patterns private (
  val ivyPatterns: Vector[String],
  val artifactPatterns: Vector[String],
  val isMavenCompatible: Boolean,
  val descriptorOptional: Boolean,
  val skipConsistencyCheck: Boolean) extends Serializable {
  
  private def this() = this(Vector.empty, Vector.empty, true, false, false)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: Patterns => (this.ivyPatterns == x.ivyPatterns) && (this.artifactPatterns == x.artifactPatterns) && (this.isMavenCompatible == x.isMavenCompatible) && (this.descriptorOptional == x.descriptorOptional) && (this.skipConsistencyCheck == x.skipConsistencyCheck)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.Patterns".##) + ivyPatterns.##) + artifactPatterns.##) + isMavenCompatible.##) + descriptorOptional.##) + skipConsistencyCheck.##)
  }
  override def toString: String = {
    "Patterns(ivyPatterns=%s, artifactPatterns=%s, isMavenCompatible=%s, descriptorOptional=%s, skipConsistencyCheck=%s)".format(
    ivyPatterns, artifactPatterns, isMavenCompatible, descriptorOptional, skipConsistencyCheck)
  }
  private[this] def copy(ivyPatterns: Vector[String] = ivyPatterns, artifactPatterns: Vector[String] = artifactPatterns, isMavenCompatible: Boolean = isMavenCompatible, descriptorOptional: Boolean = descriptorOptional, skipConsistencyCheck: Boolean = skipConsistencyCheck): Patterns = {
    new Patterns(ivyPatterns, artifactPatterns, isMavenCompatible, descriptorOptional, skipConsistencyCheck)
  }
  def withIvyPatterns(ivyPatterns: Vector[String]): Patterns = {
    copy(ivyPatterns = ivyPatterns)
  }
  def withArtifactPatterns(artifactPatterns: Vector[String]): Patterns = {
    copy(artifactPatterns = artifactPatterns)
  }
  def withIsMavenCompatible(isMavenCompatible: Boolean): Patterns = {
    copy(isMavenCompatible = isMavenCompatible)
  }
  def withDescriptorOptional(descriptorOptional: Boolean): Patterns = {
    copy(descriptorOptional = descriptorOptional)
  }
  def withSkipConsistencyCheck(skipConsistencyCheck: Boolean): Patterns = {
    copy(skipConsistencyCheck = skipConsistencyCheck)
  }
}
object Patterns extends sbt.librarymanagement.PatternsFunctions {
  
  def apply(): Patterns = new Patterns()
  def apply(ivyPatterns: Vector[String], artifactPatterns: Vector[String], isMavenCompatible: Boolean, descriptorOptional: Boolean, skipConsistencyCheck: Boolean): Patterns = new Patterns(ivyPatterns, artifactPatterns, isMavenCompatible, descriptorOptional, skipConsistencyCheck)
}
