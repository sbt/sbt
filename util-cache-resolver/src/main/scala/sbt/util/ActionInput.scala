package sbt.util

/**
 * An action input is a wrapper around hash.
 */
class ActionInput(hash: String):
  def inputHash: String = hash
  override def equals(o: Any): Boolean =
    o match {
      case o: ActionInput => this.inputHash == o.inputHash
      case _              => false
    }
  override def hashCode(): Int = hash.##
  override def toString(): String = s"ActionInput($hash)"
end ActionInput
