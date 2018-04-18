/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
final class JavaVersion private (
  val vendor: Option[String],
  val version: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: JavaVersion => (this.vendor == x.vendor) && (this.version == x.version)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.JavaVersion".##) + vendor.##) + version.##)
  }
  override def toString: String = {
    "JavaVersion(" + vendor + ", " + version + ")"
  }
  private[this] def copy(vendor: Option[String] = vendor, version: String = version): JavaVersion = {
    new JavaVersion(vendor, version)
  }
  def withVendor(vendor: Option[String]): JavaVersion = {
    copy(vendor = vendor)
  }
  def withVendor(vendor: String): JavaVersion = {
    copy(vendor = Option(vendor))
  }
  def withVersion(version: String): JavaVersion = {
    copy(version = version)
  }
}
object JavaVersion {
  def apply(version: String): JavaVersion = new JavaVersion(None, version)
  def apply(vendor: Option[String], version: String): JavaVersion = new JavaVersion(vendor, version)
  def apply(vendor: String, version: String): JavaVersion = new JavaVersion(Option(vendor), version)
}
