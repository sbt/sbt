/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
final class DateTime private (
  val year: Int,
  val month: Int,
  val day: Int,
  val hour: Int,
  val minute: Int,
  val second: Int) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: DateTime => (this.year == x.year) && (this.month == x.month) && (this.day == x.day) && (this.hour == x.hour) && (this.minute == x.minute) && (this.second == x.second)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.DateTime".##) + year.##) + month.##) + day.##) + hour.##) + minute.##) + second.##)
  }
  override def toString: String = {
    "DateTime(" + year + ", " + month + ", " + day + ", " + hour + ", " + minute + ", " + second + ")"
  }
  private[this] def copy(year: Int = year, month: Int = month, day: Int = day, hour: Int = hour, minute: Int = minute, second: Int = second): DateTime = {
    new DateTime(year, month, day, hour, minute, second)
  }
  def withYear(year: Int): DateTime = {
    copy(year = year)
  }
  def withMonth(month: Int): DateTime = {
    copy(month = month)
  }
  def withDay(day: Int): DateTime = {
    copy(day = day)
  }
  def withHour(hour: Int): DateTime = {
    copy(hour = hour)
  }
  def withMinute(minute: Int): DateTime = {
    copy(minute = minute)
  }
  def withSecond(second: Int): DateTime = {
    copy(second = second)
  }
}
object DateTime {
  
  def apply(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): DateTime = new DateTime(year, month, day, hour, minute, second)
}
