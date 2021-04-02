/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
/**
 * used by super shell
 * @param name name of a task
 * @param elapsedMicros current elapsed time in micro seconds
 */
final class ProgressItem private (
  val name: String,
  val elapsedMicros: Long) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ProgressItem => (this.name == x.name) && (this.elapsedMicros == x.elapsedMicros)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.util.ProgressItem".##) + name.##) + elapsedMicros.##)
  }
  override def toString: String = {
    "ProgressItem(" + name + ", " + elapsedMicros + ")"
  }
  private[this] def copy(name: String = name, elapsedMicros: Long = elapsedMicros): ProgressItem = {
    new ProgressItem(name, elapsedMicros)
  }
  def withName(name: String): ProgressItem = {
    copy(name = name)
  }
  def withElapsedMicros(elapsedMicros: Long): ProgressItem = {
    copy(elapsedMicros = elapsedMicros)
  }
}
object ProgressItem {
  
  def apply(name: String, elapsedMicros: Long): ProgressItem = new ProgressItem(name, elapsedMicros)
}
