package xsbt

// stripped down version of http://trac.assembla.com/metascala/browser/src/metascala/HLists.scala
//   Copyright (c) 2009, Jesper Nordenberg
//   new BSD license, see licenses/MetaScala

object HLists extends HLists
trait HLists
{
	object :: { def unapply[H,T<:HList](list: HCons[H,T]) = Some((list.head,list.tail)) }
	type ::[H, T <: HList] = HCons[H, T]
}

object HNil extends HNil
sealed trait HList {
	 type Head
	 type Tail <: HList
}
sealed class HNil extends HList {
	type Head = Nothing
	type Tail = HNil
	def ::[T](v : T) = HCons(v, this)
}

final case class HCons[H, T <: HList](head : H, tail : T) extends HList {
	type Head = H
	type Tail = T
	def ::[T](v : T) = HCons(v, this)
}