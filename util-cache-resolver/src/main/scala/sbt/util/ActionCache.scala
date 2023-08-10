package sbt.util

import sjsonnew.{ HashWriter, JsonFormat }
import sjsonnew.support.murmurhash.Hasher
import xsbti.VirtualFile

object ActionCache:
  def cache[I: HashWriter, O: JsonFormat](key: I, otherInputs: Long)(
      action: I => (O, Seq[VirtualFile])
  )(
      store: ActionCacheStore
  ): ActionValue[O] =
    val hash: Long = otherInputs * 13L + Hasher.hashUnsafe[I](key)
    val input = ActionInput(hash.toHexString)
    var result: Option[ActionValue[O]] = store.read[O](input)
    result match
      case Some(v) => v
      case None =>
        val (newResult, outputs) = action(key)
        result = Some(ActionValue(newResult, Nil))

        val hashedVF =
          if outputs.nonEmpty then store.writeBlobs(outputs)
          else Nil
        val value = ActionValue(newResult, hashedVF)
        store.write[O](input, value)
        result = Some(value)
        result.get
end ActionCache
