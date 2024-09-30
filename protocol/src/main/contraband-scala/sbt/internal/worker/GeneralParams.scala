/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class GeneralParams private (
  val main_class: String,
  val args: Vector[String],
  val classpath: Vector[sbt.internal.worker.HashedPath]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: GeneralParams => (this.main_class == x.main_class) && (this.args == x.args) && (this.classpath == x.classpath)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.GeneralParams".##) + main_class.##) + args.##) + classpath.##)
  }
  override def toString: String = {
    "GeneralParams(" + main_class + ", " + args + ", " + classpath + ")"
  }
  private[this] def copy(main_class: String = main_class, args: Vector[String] = args, classpath: Vector[sbt.internal.worker.HashedPath] = classpath): GeneralParams = {
    new GeneralParams(main_class, args, classpath)
  }
  def withMain_class(main_class: String): GeneralParams = {
    copy(main_class = main_class)
  }
  def withArgs(args: Vector[String]): GeneralParams = {
    copy(args = args)
  }
  def withClasspath(classpath: Vector[sbt.internal.worker.HashedPath]): GeneralParams = {
    copy(classpath = classpath)
  }
}
object GeneralParams {
  
  def apply(main_class: String, args: Vector[String], classpath: Vector[sbt.internal.worker.HashedPath]): GeneralParams = new GeneralParams(main_class, args, classpath)
}
