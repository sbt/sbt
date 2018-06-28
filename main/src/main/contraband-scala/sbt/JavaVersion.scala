/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
final class JavaVersion private (
  val numbers: Vector[Long],
  val vendor: Option[String]) extends Serializable {
  def numberStr: String = numbers.mkString(".")
  
  
  override def equals(o: Any): Boolean = o match {
    case x: JavaVersion => (this.numbers == x.numbers) && (this.vendor == x.vendor)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.JavaVersion".##) + numbers.##) + vendor.##)
  }
  override def toString: String = {
    vendor.map(_ + "@").getOrElse("") + numberStr
  }
  private[this] def copy(numbers: Vector[Long] = numbers, vendor: Option[String] = vendor): JavaVersion = {
    new JavaVersion(numbers, vendor)
  }
  def withNumbers(numbers: Vector[Long]): JavaVersion = {
    copy(numbers = numbers)
  }
  def withVendor(vendor: Option[String]): JavaVersion = {
    copy(vendor = vendor)
  }
  def withVendor(vendor: String): JavaVersion = {
    copy(vendor = Option(vendor))
  }
}
object JavaVersion {
  def apply(version: String): JavaVersion = sbt.internal.CrossJava.parseJavaVersion(version)
  def apply(numbers: Vector[Long], vendor: Option[String]): JavaVersion = new JavaVersion(numbers, vendor)
  def apply(numbers: Vector[Long], vendor: String): JavaVersion = new JavaVersion(numbers, Option(vendor))
}
