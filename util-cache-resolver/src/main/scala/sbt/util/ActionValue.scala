package sbt.util

import scala.reflect.ClassTag
import sjsonnew.*
import xsbti.HashedVirtualFileRef

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

object ActionValue:
  import CacheImplicits.*

  given [A1: ClassTag: JsonFormat]
      : IsoLList.Aux[ActionValue[A1], A1 :*: Vector[HashedVirtualFileRef] :*: LNil] =
    LList.iso(
      { (v: ActionValue[A1]) =>
        ("value", v.value) :*: ("outputs", v.outputs.toVector) :*: LNil
      },
      { (in: A1 :*: Vector[HashedVirtualFileRef] :*: LNil) =>
        ActionValue(in.head, in.tail.head)
      }
    )
end ActionValue
