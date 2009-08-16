package xsbt

import metascala.HLists.{HCons => metaHCons, HList => metaHList, HNil => metaHNil}

object HLists extends HLists
// add an extractor to metascala.HLists and define aliases to the HList classes in the xsbt namespace
trait HLists extends NotNull
{
	object :: { def unapply[H,T<:HList](list: HCons[H,T]) = Some((list.head,list.tail)) }
	final val HNil = metaHNil
	final type ::[H, T <: HList] = metaHCons[H, T]
	final type HNil = metaHNil
	final type HList = metaHList
	final type HCons[H, T <: HList] = metaHCons[H, T]
}