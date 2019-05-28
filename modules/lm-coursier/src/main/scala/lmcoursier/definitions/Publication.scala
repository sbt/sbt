/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
final class Publication private (
  val name: String,
  val `type`: Type,
  val ext: Extension,
  val classifier: Classifier) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Publication => (this.name == x.name) && (this.`type` == x.`type`) && (this.ext == x.ext) && (this.classifier == x.classifier)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.Publication".##) + name.##) + `type`.##) + ext.##) + classifier.##)
  }
  override def toString: String = {
    "Publication(" + name + ", " + `type` + ", " + ext + ", " + classifier + ")"
  }
  private[this] def copy(name: String = name, `type`: Type = `type`, ext: Extension = ext, classifier: Classifier = classifier): Publication = {
    new Publication(name, `type`, ext, classifier)
  }
  def withName(name: String): Publication = {
    copy(name = name)
  }
  def withType(`type`: Type): Publication = {
    copy(`type` = `type`)
  }
  def withExt(ext: Extension): Publication = {
    copy(ext = ext)
  }
  def withClassifier(classifier: Classifier): Publication = {
    copy(classifier = classifier)
  }
}
object Publication {
  
  def apply(name: String, `type`: Type, ext: Extension, classifier: Classifier): Publication = new Publication(name, `type`, ext, classifier)
}
