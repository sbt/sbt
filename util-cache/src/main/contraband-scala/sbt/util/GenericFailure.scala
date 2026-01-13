/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.util
/**
 * A GenericFailure represents a cached task failure.
 * This allows caching failures so that repeated builds don't re-run failed tasks.
 * The kind field indicates the type of failure (e.g., "CompileFailed").
 * 
 * Fixes https://github.com/sbt/sbt/issues/7662
 */
final class GenericFailure private (
  val kind: Option[String],
  val message: Option[String],
  val problems: Vector[xsbti.Problem]) extends Serializable {
  
  private def this() = this(None, None, Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: GenericFailure => (this.kind == x.kind) && (this.message == x.message) && (this.problems == x.problems)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.util.GenericFailure".##) + kind.##) + message.##) + problems.##)
  }
  override def toString: String = {
    "GenericFailure(" + kind + ", " + message + ", " + problems + ")"
  }
  private def copy(kind: Option[String] = kind, message: Option[String] = message, problems: Vector[xsbti.Problem] = problems): GenericFailure = {
    new GenericFailure(kind, message, problems)
  }
  def withKind(kind: Option[String]): GenericFailure = {
    copy(kind = kind)
  }
  def withKind(kind: String): GenericFailure = {
    copy(kind = Option(kind))
  }
  def withMessage(message: Option[String]): GenericFailure = {
    copy(message = message)
  }
  def withMessage(message: String): GenericFailure = {
    copy(message = Option(message))
  }
  def withProblems(problems: Vector[xsbti.Problem]): GenericFailure = {
    copy(problems = problems)
  }
}
object GenericFailure {
  
  def apply(): GenericFailure = new GenericFailure()
  def apply(kind: Option[String], message: Option[String], problems: Vector[xsbti.Problem]): GenericFailure = new GenericFailure(kind, message, problems)
  def apply(kind: String, message: String, problems: Vector[xsbti.Problem]): GenericFailure = new GenericFailure(Option(kind), Option(message), problems)
}
