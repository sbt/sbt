package sbt.wrap

import org.scalacheck._
import java.util.{HashSet, LinkedHashSet}

object WrappedHashSetTests extends MutableSetWrapperTests(new HashSet[Int])
object WrappedLinkedHashSetTests extends MutableSetWrapperTests(new LinkedHashSet[Int])
{
	specify("toList preserves order", checkAddOrdered _)

	private def checkAddOrdered(values: List[Int]) =
	{
		val set = createWrapped
		val check = new scala.collection.mutable.HashSet[Int]
		val list = new scala.collection.mutable.ListBuffer[Int]
		for(value <- values)
		{
			set += value
			if(!check(value))
			{
				check += value
				list += value
			}
		}
		list.toList sameElements set.toList
	}
}

abstract class MutableSetWrapperTests(createUnderlying: => java.util.Set[Int]) extends Properties("Mutable Set wrapper (" + createUnderlying.getClass.getName + ")")
{
	protected def createWrapped = new MutableSetWrapper(createUnderlying)

	specify("Contains all added at once", checkBatchAddition _)
	specify("Contains all added individually", checkSingleAddition _)
	specify("toList contains all added at once", checkBatchToList _)
	specify("toList contains all added individually", checkSingleToList _)
	specify("Contains all added and not removed", checkRemove _)

	private def checkSingleAddition(values: List[Int]) =
	{
		val set = createSingleAdd(values)
		values.forall(set.contains)
	}
	private def checkBatchAddition(values: List[Int]) =
	{
		val set = createBatchAdd(values)
		values.forall(set.contains)
	}
	private def checkBatchToList(values: List[Int]) =
	{
		val set = createBatchAdd(values)
		val check = scala.collection.mutable.HashSet(set.toList : _*)
		values.forall(check.contains)
	}
	private def checkSingleToList(values: List[Int]) =
	{
		val set = createSingleAdd(values)
		val check = scala.collection.mutable.HashSet(set.toList : _*)
		values.forall(check.contains)
	}
	protected final def createBatchAdd(values: List[Int]) =
	{
		val set = createWrapped
		set ++= values
		set
	}
	protected final def createSingleAdd(values: List[Int]) =
	{
		val set = createWrapped
		values.foreach(set += _)
		set
	}
	private def checkRemove(values: List[(Int, Boolean)]) =
	{
		val set = createWrapped
		val check = new scala.collection.mutable.HashSet[Int]
		for( (key, _) <- values)
		{
			set += key
			check += key
		}
		for( (key, false) <- values)
		{
			set -= key
			check -= key
		}
		values.forall { case (key, _) => set.contains(key) == check.contains(key) }
	}
}