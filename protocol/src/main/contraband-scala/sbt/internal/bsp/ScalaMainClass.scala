/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param class The main class to run.
 * @param arguments The user arguments to the main entrypoint.
 * @param jvmOptions The jvm options for the application.
 */
final class ScalaMainClass private (
  val `class`: String,
  val arguments: Vector[String],
  val jvmOptions: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaMainClass => (this.`class` == x.`class`) && (this.arguments == x.arguments) && (this.jvmOptions == x.jvmOptions)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaMainClass".##) + `class`.##) + arguments.##) + jvmOptions.##)
  }
  override def toString: String = {
    "ScalaMainClass(" + `class` + ", " + arguments + ", " + jvmOptions + ")"
  }
  private[this] def copy(`class`: String = `class`, arguments: Vector[String] = arguments, jvmOptions: Vector[String] = jvmOptions): ScalaMainClass = {
    new ScalaMainClass(`class`, arguments, jvmOptions)
  }
  def withClass(`class`: String): ScalaMainClass = {
    copy(`class` = `class`)
  }
  def withArguments(arguments: Vector[String]): ScalaMainClass = {
    copy(arguments = arguments)
  }
  def withJvmOptions(jvmOptions: Vector[String]): ScalaMainClass = {
    copy(jvmOptions = jvmOptions)
  }
}
object ScalaMainClass {
  
  def apply(`class`: String, arguments: Vector[String], jvmOptions: Vector[String]): ScalaMainClass = new ScalaMainClass(`class`, arguments, jvmOptions)
}
