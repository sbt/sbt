package sbt.util

import scala.reflect.ClassTag
import sjsonnew.*
import xsbti.HashedVirtualFileRef

/**
 * An action result represents a result from excuting a task.
 * In addition to the value typically represented in the return type
 * of the task, action value tracks the file output side effect.
 */
class ActionResult[A1](a: A1, outs: Seq[HashedVirtualFileRef]):
  def value: A1 = a
  def outputFiles: Seq[HashedVirtualFileRef] = outs
  override def equals(o: Any): Boolean =
    o match {
      case o: ActionResult[a] => this.value == o.value && this.outputFiles == o.outputFiles
      case _                  => false
    }
  override def hashCode(): Int = (a, outs).##
  override def toString(): String = s"ActionResult($a, $outs)"
end ActionResult

object ActionResult:
  import CacheImplicits.*

  given [A1: ClassTag: JsonFormat]
      : IsoLList.Aux[ActionResult[A1], A1 :*: Vector[HashedVirtualFileRef] :*: LNil] =
    LList.iso(
      { (v: ActionResult[A1]) =>
        ("value", v.value) :*: ("outputFiles", v.outputFiles.toVector) :*: LNil
      },
      { (in: A1 :*: Vector[HashedVirtualFileRef] :*: LNil) =>
        ActionResult(in.head, in.tail.head)
      }
    )
end ActionResult
