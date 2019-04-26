package lmcoursier.definitions

final class Attributes private (
  val `type`: Type,
  val classifier: Classifier
) {

  override def equals(obj: Any): Boolean =
    obj match {
      case other: Attributes =>
        `type` == other.`type` && classifier == other.classifier
      case _ => false
    }
  override def hashCode(): Int =
    37 * (37 * (37 * (17 + "lmcoursier.definitions.Attributes".##) + `type`.##) + classifier.##)
  override def toString: String =
    s"Attributes(${`type`}, $classifier)"

  private def copy(
    `type`: Type = `type`,
    classifier: Classifier = classifier
  ): Attributes =
    new Attributes(`type`, classifier)

  def withType(`type`: Type): Attributes =
    copy(`type` = `type`)
  def withClassifier(classifier: Classifier): Attributes =
    copy(classifier = classifier)

}

object Attributes {
  def apply(`type`: Type, classifier: Classifier): Attributes =
    new Attributes(`type`, classifier)
}
