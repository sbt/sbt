/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Clean Cache Response
 * @param message Optional message to display to the user
 * @param cleaned Indicates whether the clean cache request was performed or not
 */
final class CleanCacheResult private (
  val message: Option[String],
  val cleaned: Boolean) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CleanCacheResult => (this.message == x.message) && (this.cleaned == x.cleaned)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.CleanCacheResult".##) + message.##) + cleaned.##)
  }
  override def toString: String = {
    "CleanCacheResult(" + message + ", " + cleaned + ")"
  }
  private[this] def copy(message: Option[String] = message, cleaned: Boolean = cleaned): CleanCacheResult = {
    new CleanCacheResult(message, cleaned)
  }
  def withMessage(message: Option[String]): CleanCacheResult = {
    copy(message = message)
  }
  def withMessage(message: String): CleanCacheResult = {
    copy(message = Option(message))
  }
  def withCleaned(cleaned: Boolean): CleanCacheResult = {
    copy(cleaned = cleaned)
  }
}
object CleanCacheResult {
  
  def apply(message: Option[String], cleaned: Boolean): CleanCacheResult = new CleanCacheResult(message, cleaned)
  def apply(message: String, cleaned: Boolean): CleanCacheResult = new CleanCacheResult(Option(message), cleaned)
}
