package xsbt.boot

import java.lang.ref.{Reference, SoftReference}
import java.util.HashMap

final class Cache[K,V](create: K => V) extends NotNull
{
	private[this] val delegate = new HashMap[K,Reference[V]]
	def apply(k: K): V = getFromReference(k, delegate.get(k))
	private[this] def getFromReference(k: K, existingRef: Reference[V]) = if(existingRef eq null) newEntry(k) else get(k, existingRef.get)
	private[this] def get(k: K, existing: V) = if(existing == null) newEntry(k) else existing
	private[this] def newEntry(k: K): V =
	{
		val v = create(k)
		Pre.assert(v != null, "Value for key " + k + " was null")
		delegate.put(k, new SoftReference(v))
		v
	}
}
