package sbt.util

import sjsonnew.{ HashWriter, JsonFormat }
import sjsonnew.support.murmurhash.Hasher
import xsbti.VirtualFile

object ActionCache:
  def cache[I: HashWriter, O: JsonFormat](key: I)(action: I => (O, Seq[VirtualFile]))(
      stores: Seq[ActionCacheStore]
  ): ActionValue[O] =
    var result: Option[ActionValue[O]] = None
    val hash: String = Hasher.hashUnsafe[I](key).toHexString
    val input = ActionInput(hash)
    stores.foreach: store =>
      if result.isEmpty then
        val r = store.read[O](input)
        result = r
    result match
      case Some(v) => v
      case None =>
        val (newResult, outputs) = action(key)
        stores.foreach: store =>
          val hashedVF =
            if outputs.nonEmpty then store.writeBlobs(outputs)
            else Nil
          val value = ActionValue(newResult, hashedVF)
          store.write[O](input, value)
          result = Some(value)
        result.get
end ActionCache
