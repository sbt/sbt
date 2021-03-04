/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
/**
 * Mini version of sbt.testing.Event
 * @param fullyQualifiedName The fully qualified name of a class that can rerun the suite or test
                             about which an event was fired.
 * @param status Indicates whether the event represents a test success, failure, error, skipped, ignored, canceled, pending.
 * @param duration An amount of time, in milliseconds, that was required to complete the action reported by this event.
                   None, if no duration was available.
 */
final class TestItemDetail private (
  val fullyQualifiedName: String,
  val status: sbt.testing.Status,
  val duration: Option[Long]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TestItemDetail => (this.fullyQualifiedName == x.fullyQualifiedName) && (this.status == x.status) && (this.duration == x.duration)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.protocol.testing.TestItemDetail".##) + fullyQualifiedName.##) + status.##) + duration.##)
  }
  override def toString: String = {
    "TestItemDetail(" + fullyQualifiedName + ", " + status + ", " + duration + ")"
  }
  private[this] def copy(fullyQualifiedName: String = fullyQualifiedName, status: sbt.testing.Status = status, duration: Option[Long] = duration): TestItemDetail = {
    new TestItemDetail(fullyQualifiedName, status, duration)
  }
  def withFullyQualifiedName(fullyQualifiedName: String): TestItemDetail = {
    copy(fullyQualifiedName = fullyQualifiedName)
  }
  def withStatus(status: sbt.testing.Status): TestItemDetail = {
    copy(status = status)
  }
  def withDuration(duration: Option[Long]): TestItemDetail = {
    copy(duration = duration)
  }
  def withDuration(duration: Long): TestItemDetail = {
    copy(duration = Option(duration))
  }
}
object TestItemDetail {
  
  def apply(fullyQualifiedName: String, status: sbt.testing.Status, duration: Option[Long]): TestItemDetail = new TestItemDetail(fullyQualifiedName, status, duration)
  def apply(fullyQualifiedName: String, status: sbt.testing.Status, duration: Long): TestItemDetail = new TestItemDetail(fullyQualifiedName, status, Option(duration))
}
