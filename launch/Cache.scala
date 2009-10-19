package xsbt.boot

import java.util.HashMap

final class Cache[K,V](create: K => V) extends NotNull
{
	private[this] val delegate = new HashMap[K,V]
	def apply(k: K): V =
	{
		val existing = delegate.get(k)
		if(existing == null) newEntry(k) else existing
	}
	private[this] def newEntry(k: K): V =
	{
		val v = create(k)
		Pre.assert(v != null, "Value for key " + k + " was null")
		delegate.put(k, v)
		v
	}
}
