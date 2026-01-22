/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.internal
final class ArtifactLock private (
  val url: String,
  val classifier: Option[String],
  val extension: String,
  val tpe: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ArtifactLock => (this.url == x.url) && (this.classifier == x.classifier) && (this.extension == x.extension) && (this.tpe == x.tpe)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.internal.ArtifactLock".##) + url.##) + classifier.##) + extension.##) + tpe.##)
  }
  override def toString: String = {
    "ArtifactLock(" + url + ", " + classifier + ", " + extension + ", " + tpe + ")"
  }
  private def copy(url: String = url, classifier: Option[String] = classifier, extension: String = extension, tpe: String = tpe): ArtifactLock = {
    new ArtifactLock(url, classifier, extension, tpe)
  }
  def withUrl(url: String): ArtifactLock = {
    copy(url = url)
  }
  def withClassifier(classifier: Option[String]): ArtifactLock = {
    copy(classifier = classifier)
  }
  def withClassifier(classifier: String): ArtifactLock = {
    copy(classifier = Option(classifier))
  }
  def withExtension(extension: String): ArtifactLock = {
    copy(extension = extension)
  }
  def withTpe(tpe: String): ArtifactLock = {
    copy(tpe = tpe)
  }
}
object ArtifactLock {
  
  def apply(url: String, classifier: Option[String], extension: String, tpe: String): ArtifactLock = new ArtifactLock(url, classifier, extension, tpe)
  def apply(url: String, classifier: String, extension: String, tpe: String): ArtifactLock = new ArtifactLock(url, Option(classifier), extension, tpe)
}
