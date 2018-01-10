package coursier

object SbtCompatibility {
  def needsIvyXmlLocal = List(sbt.Keys.deliverLocalConfiguration)
  def needsIvyXml = List(sbt.Keys.deliverConfiguration)
}
