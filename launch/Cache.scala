package xsbt.boot

import java.lang.ref.{Reference, SoftReference}
import java.util.HashMap

final class Cache[K,V](create: K => V) extends NotNull
{
	private[this] val delegate = new HashMap[K,Reference[V]]
	def apply(k: K): V =
	{
		val existingRef = delegate.get(k)
		if(existingRef == null)
			newEntry(k)
		else
		{
			val existing = existingRef.get
			if(existing == null)
			{
				println("Cache value for '" + k + "' was garbage collected, recreating it...")
				newEntry(k)
			}
			else
				existing
		}
	}
	private[this] def newEntry(k: K): V =
	{
		val v = create(k)
		Pre.assert(v != null, "Value for key " + k + " was null")
		delegate.put(k, new SoftReference(v))
		v
	}
}
