package sbt

	import Types._

final case class ReducedH[K[_], HL <: HList, HLk <: HList](keys: KList[K, HLk], expand: HLk => HL)
{
	def prepend[T](key: K[T])(g: K ~> Option) =
		g(key) match
		{
			case None => ReducedH[K, T :+: HL, T :+: HLk]( KCons(key, keys), { case v :+: hli => HCons(v, expand(hli)) })
			case Some(v) => ReducedH[K, T :+: HL, HLk](keys, hli => HCons(v, expand(hli)))
		}
	def combine[T](f: (KList[K, HLk], HLk => HL) => T): T = f(keys, expand)
}
final case class ReducedK[K[_], M[_], HL <: HList, HLk <: HList](keys: KList[(K ∙ M)#l, HLk], expand: KList[M, HLk] => KList[M, HL])
{
	def prepend[T](key: K[M[T]])(g: (K ∙ M)#l ~> (Option ∙ M)#l): ReducedK[K, M, T :+: HL, _ <: HList] =
		g(key) match
		{
			case None => ReducedK[K, M, T :+: HL, T :+: HLk]( KCons[T, HLk, (K ∙ M)#l](key, keys), { case KCons(v, hli) => KCons(v, expand(hli)) })
			case Some(v) => ReducedK[K, M, T :+: HL, HLk](keys, hli => KCons(v, expand(hli)) )
		}
	def combine[T](f: (KList[(K ∙ M)#l, HLk], KList[M, HLk] => KList[M, HL]) => T): T = f(keys, expand)
}
final case class ReducedSeq[K[_], T](keys: Seq[K[T]], expand: Seq[T] => Seq[T])
{
	def prepend(key: K[T])(g: K ~> Option) =
		g(key) match
		{
			case None => ReducedSeq[K, T](key +: keys, { case Seq(x, xs @ _*) => x +: expand(xs) })
			case Some(v) => ReducedSeq[K, T](keys, xs => v +: expand(xs))
		}
}
object Reduced
{
	def reduceK[HL <: HList, K[_], M[_]](keys: KList[(K ∙ M)#l, HL], g: (K ∙ M)#l ~> (Option ∙ M)#l): ReducedK[K, M, HL, _ <: HList] =
	{
		type RedK[HL <: HList] = ReducedK[K, M, HL, _ <: HList]
		keys.foldr[RedK,(K ∙ M)#l] { new KFold[(K ∙ M)#l, RedK] {
			def knil = emptyK
			def kcons[H,T<:HList](h: K[M[H]], acc: RedK[T]): RedK[H :+: T] =
				acc.prepend(h)(g)
		}}
	}
	def reduceH[HL <: HList, K[_]](keys: KList[K, HL], g: K ~> Option): ReducedH[K, HL, _ <: HList] =
	{
		type RedH[HL <: HList] = ReducedH[K, HL, _ <: HList]
		keys.foldr { new KFold[K, RedH] {
			def knil = emptyH
			def kcons[H,T<:HList](h: K[H], acc: RedH[T]): RedH[H :+: T] =
				acc.prepend(h)(g)
		}}
	}
	def reduceSeq[K[_], T](keys: Seq[K[T]], g: K ~> Option): ReducedSeq[K, T] = (ReducedSeq[K, T](Nil, idFun) /: keys) { (red, key) => red.prepend(key)(g) }
	def emptyH[K[_]] = ReducedH[K, HNil, HNil](KNil, idFun)
	def emptyK[K[_], M[_]] = ReducedK[K, M, HNil, HNil](KNil, idFun)
}
/*

def mapConstant(inputs: Seq[ScopedKey[S]], g: ScopedKey ~> Option) = 
	split(Nil, inputs.head, inputs.tails, g) match
	{
		None => new Uniform(f, inputs)
		Some((left, x, Nil)) => new Uniform(in => f(in :+ x), left)
		Some((Nil, x, right)) => new Uniform(in => f(x +: in), right)
		Some((left, x, right)) => new Joined(uniformID(left), mapConstant(right), (l,r) => (l ++ (x +: r)) )
	}
def split[S, M[_]](acc: List[M[S]], head: M[S], tail: List[M[S]], f: M ~> Option): Option[(Seq[M[S]], S, Seq[M[S]])] =
	(f(head), tail) match
	{
		case (None, Nil) => None
		case (None, x :: xs) => split( head :: acc, x, xs, f)
		case (Some(v), ts) => Some( (acc.reverse, v, ts) )
	}
*/