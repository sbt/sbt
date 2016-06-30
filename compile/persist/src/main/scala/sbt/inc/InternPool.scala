package sbt
package inc


import sbinary._
import DefaultProtocol._
import xsbti.api.Lazy
import xsbti.SafeLazy


/**
 * A simple intern pool for sharing references to a unique instance of some immutable type.
 * The primary purpose of this class is to improve performance of analysis serialization/deserialization.
 *
 * Pooling occurs on serialization: all equal objects are serialized as a 32-bit integer index of a single representative. 
 * This allows us to benefit from pooling without changing all the object allocation sites.
 *
 * On deserialization that single representative is used where previously there might have been multiple equal instances.
 * Thus, round-tripping an analysis may yield a smaller in-memory object graph.
 *
 * Note that the set of pooled items must be known before serialization begins. We can't build it up on-the-fly as
 * we serialize: We must serialize the entire pool before serializing anything that uses it, as we'll need to read the
 * entire pool in first when deserializing. The InternPool is immutable to enforce this.
 */
class InternPool[T <: AnyRef](itemsArray: Array[T]) extends Serializable {
	def toIdx(item: T): Int = itemToIdx.get(item) match {
		case None => sys.error("No such item in intern pool: %s".format(item.toString))
		case Some(x) => x
	}

	def toItem(idx: Int): T = if (idx >= 0 && idx < items.length) items(idx) else sys.error("No such index in intern pool: %d".format(idx))

	def allItems: Array[T] = items

	private[this] val items = itemsArray
	private[this] val itemToIdx = Map.empty[T, Int] ++ itemsArray.zipWithIndex
}


/**
 * Serialization formats that use an InternPool.
 *
 * fullFormat is the format to use for T when serializing the pool itself.
 */
class InternPoolFormats[T <: AnyRef](fullFormat: Format[T])(implicit mf: Manifest[T]) {
	var pool: Option[InternPool[T]] = None

	/**
	 * Create the intern pool immediately before writing it/after reading it, so it can be used for subsequent writes/reads.
	 */
	def initPool(items: Array[T]): InternPool[T] = {
		pool = Some(new InternPool[T](items))
		pool.get
	}

	/** Format for writing a T as a pool reference. */
	implicit def itemFormat: Format[T] = wrap[T, Int](toIdx, toItem)

	/** Format for writing a T as a lazily-resolved pool reference. */
	implicit def lazyItemFormat: Format[Lazy[T]] = wrap[Lazy[T], Int](x => toIdx(x.get), idx => SafeLazy.apply[T](toItem(idx)))

	/** Format for writing an array of T as a lazily-resolved pool reference. */
	implicit def lazyItemsFormat: Format[Lazy[Array[T]]] = wrap[Lazy[Array[T]], Array[Int]](
		x => x.get map toIdx,
		idxes => SafeLazy.apply[Array[T]](idxes map toItem)
	)

	/** Format for writing the pool itself. */
	implicit def poolFormat: Format[InternPool[T]] = wrap[InternPool[T], Array[T]](_.allItems, initPool)(arrayFormat(fullFormat, mf))

	private[this] def toIdx(item: T): Int = try {
		pool.get.toIdx(item)
	} catch {
		case e: NoSuchElementException => throw new RuntimeException("Intern pool not available for " + mf.runtimeClass.getName)
	}

	private[this] def toItem(idx: Int): T = try {
		pool.get.toItem(idx)
	} catch {
		case e: NoSuchElementException => throw new RuntimeException("Intern pool not available for " + mf.runtimeClass.getName)
	}
}
