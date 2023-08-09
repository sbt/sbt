package sbt.util

/**
 * An action value represents a result from excuting a task.
 * In addition to the value typically represented in the return type
 * of the task, action value tracks the file output side effect.
 */
class ActionValue[A1](a: A1, outs: Seq[HashedVirtualFileRef]):
  def value: A1 = a
  def outputs: Seq[HashedVirtualFileRef] = outs
  override def equals(o: Any): Boolean =
    o match {
      case o: ActionValue[a] => this.value == o.value && this.outputs == o.outputs
      case _                 => false
    }
  override def hashCode(): Int = (a, outs).##
  override def toString(): String = s"ActionValue($a, $outs)"
end ActionValue
