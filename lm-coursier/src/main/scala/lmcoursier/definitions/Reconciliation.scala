package lmcoursier.definitions

sealed abstract class Reconciliation extends Serializable

object Reconciliation {
  case object Default extends Reconciliation
  case object Relaxed extends Reconciliation
  case object Strict extends Reconciliation
  case object SemVer extends Reconciliation

  def apply(input: String): Option[Reconciliation] =
    input match {
      case "default" => Some(Default)
      case "relaxed" => Some(Relaxed)
      case "strict" => Some(Strict)
      case "semver" => Some(SemVer)
      case _ => None
    }
}
