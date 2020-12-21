/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
final class JavaVersion private (
  val numbers: Vector[Long],
  val tags: Vector[String],
  val vendor: Option[String]) extends Serializable {
  def numberStr: String = numbers.mkString(".")
  private def tagStr: String = if (tags.isEmpty) "" else tags.mkString("-", "-", "")
  private def this() = this(Vector(), Vector(), None)
  private def this(numbers: Vector[Long], vendor: Option[String]) = this(numbers, Vector(), vendor)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JavaVersion => (this.numbers == x.numbers) && (this.tags == x.tags) && (this.vendor == x.vendor)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.JavaVersion".##) + numbers.##) + tags.##) + vendor.##)
  }
  override def toString: String = {
    vendor.map(_ + "@").getOrElse("") + numberStr + tagStr
  }
  private[this] def copy(numbers: Vector[Long] = numbers, tags: Vector[String] = tags, vendor: Option[String] = vendor): JavaVersion = {
    new JavaVersion(numbers, tags, vendor)
  }
  def withNumbers(numbers: Vector[Long]): JavaVersion = {
    copy(numbers = numbers)
  }
  def withTags(tags: Vector[String]): JavaVersion = {
    copy(tags = tags)
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
  def apply(): JavaVersion = new JavaVersion()
  def apply(numbers: Vector[Long], vendor: Option[String]): JavaVersion = new JavaVersion(numbers, vendor)
  def apply(numbers: Vector[Long], vendor: String): JavaVersion = new JavaVersion(numbers, Option(vendor))
  def apply(numbers: Vector[Long], tags: Vector[String], vendor: Option[String]): JavaVersion = new JavaVersion(numbers, tags, vendor)
  def apply(numbers: Vector[Long], tags: Vector[String], vendor: String): JavaVersion = new JavaVersion(numbers, tags, Option(vendor))
}
