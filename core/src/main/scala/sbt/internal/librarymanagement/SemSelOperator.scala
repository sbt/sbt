package sbt.internal.librarymanagement
sealed abstract class SemSelOperator {
  override def toString: String = this match {
    case SemSelOperator.Lte => "<="
    case SemSelOperator.Lt  => "<"
    case SemSelOperator.Gte => ">="
    case SemSelOperator.Gt  => ">"
    case SemSelOperator.Eq  => "="
  }
}
object SemSelOperator {
  case object Lte extends SemSelOperator
  case object Lt extends SemSelOperator
  case object Gte extends SemSelOperator
  case object Gt extends SemSelOperator
  case object Eq extends SemSelOperator
}
