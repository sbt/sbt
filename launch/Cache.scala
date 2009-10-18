package xsbt.boot

import java.util.HashMap

final class Cache[K,V <: AnyRef](create: K => V) extends NotNull
{
	private[this] val delegate = new HashMap[K,V]
	def apply(k: K): V =
	{
		val existing = delegate.get(k)
		if(existing eq null) newEntry(k) else existing
	}
	private[this] def newEntry(k: K): V =
	{
		val v = create(k)
		delegate.put(k, v)
		v
	}
}