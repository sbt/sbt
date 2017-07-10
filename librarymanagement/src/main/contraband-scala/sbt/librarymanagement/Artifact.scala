/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class Artifact private (
  val name: String,
  val `type`: String,
  val extension: String,
  val classifier: Option[String],
  val configurations: Vector[sbt.librarymanagement.ConfigRef],
  val url: Option[java.net.URL],
  val extraAttributes: Map[String, String],
  val checksum: Option[sbt.librarymanagement.Checksum]) extends sbt.librarymanagement.ArtifactExtra with Serializable {
  
  private def this(name: String) = this(name, Artifact.DefaultType, Artifact.DefaultExtension, None, Vector.empty, None, Map.empty, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: Artifact => (this.name == x.name) && (this.`type` == x.`type`) && (this.extension == x.extension) && (this.classifier == x.classifier) && (this.configurations == x.configurations) && (this.url == x.url) && (this.extraAttributes == x.extraAttributes) && (this.checksum == x.checksum)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "Artifact".##) + name.##) + `type`.##) + extension.##) + classifier.##) + configurations.##) + url.##) + extraAttributes.##) + checksum.##)
  }
  override def toString: String = {
    "Artifact(" + name + ", " + `type` + ", " + extension + ", " + classifier + ", " + configurations + ", " + url + ", " + extraAttributes + ", " + checksum + ")"
  }
  protected[this] def copy(name: String = name, `type`: String = `type`, extension: String = extension, classifier: Option[String] = classifier, configurations: Vector[sbt.librarymanagement.ConfigRef] = configurations, url: Option[java.net.URL] = url, extraAttributes: Map[String, String] = extraAttributes, checksum: Option[sbt.librarymanagement.Checksum] = checksum): Artifact = {
    new Artifact(name, `type`, extension, classifier, configurations, url, extraAttributes, checksum)
  }
  def withName(name: String): Artifact = {
    copy(name = name)
  }
  def withType(`type`: String): Artifact = {
    copy(`type` = `type`)
  }
  def withExtension(extension: String): Artifact = {
    copy(extension = extension)
  }
  def withClassifier(classifier: Option[String]): Artifact = {
    copy(classifier = classifier)
  }
  def withConfigurations(configurations: Vector[sbt.librarymanagement.ConfigRef]): Artifact = {
    copy(configurations = configurations)
  }
  def withUrl(url: Option[java.net.URL]): Artifact = {
    copy(url = url)
  }
  def withExtraAttributes(extraAttributes: Map[String, String]): Artifact = {
    copy(extraAttributes = extraAttributes)
  }
  def withChecksum(checksum: Option[sbt.librarymanagement.Checksum]): Artifact = {
    copy(checksum = checksum)
  }
}
object Artifact extends sbt.librarymanagement.ArtifactFunctions {
  
  def apply(name: String): Artifact = new Artifact(name, Artifact.DefaultType, Artifact.DefaultExtension, None, Vector.empty, None, Map.empty, None)
  def apply(name: String, `type`: String, extension: String, classifier: Option[String], configurations: Vector[sbt.librarymanagement.ConfigRef], url: Option[java.net.URL], extraAttributes: Map[String, String], checksum: Option[sbt.librarymanagement.Checksum]): Artifact = new Artifact(name, `type`, extension, classifier, configurations, url, extraAttributes, checksum)
}
