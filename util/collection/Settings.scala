/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Types._
	import annotation.tailrec
	import collection.mutable

sealed trait Settings[Scope]
{
	def data: Map[Scope, AttributeMap]
	def keys(scope: Scope): Set[AttributeKey[_]]
	def scopes: Set[Scope]
	def get[T](scope: Scope, key: AttributeKey[T]): Option[T]
	def set[T](scope: Scope, key: AttributeKey[T], value: T): Settings[Scope]
}

private final class Settings0[Scope](val data: Map[Scope, AttributeMap], val delegates: Scope => Seq[Scope]) extends Settings[Scope]
{
	def scopes: Set[Scope] = data.keySet.toSet
	def keys(scope: Scope) = data(scope).keys.toSet

	def get[T](scope: Scope, key: AttributeKey[T]): Option[T] =
		delegates(scope).toStream.flatMap(sc => scopeLocal(sc, key) ).headOption

	private def scopeLocal[T](scope: Scope, key: AttributeKey[T]): Option[T] =
		(data get scope).flatMap(_ get key)

	def set[T](scope: Scope, key: AttributeKey[T], value: T): Settings[Scope] =
	{
		val map = (data get scope) getOrElse AttributeMap.empty
		val newData = data.updated(scope, map.put(key, value))
		new Settings0(newData, delegates)
	}
}
// delegates should contain the input Scope as the first entry
// this trait is intended to be mixed into an object
trait Init[Scope]
{
	final case class ScopedKey[T](scope: Scope, key: AttributeKey[T])

	type SettingSeq[T] = Seq[Setting[T]]
	type ScopedMap = IMap[ScopedKey, SettingSeq]
	type CompiledMap = Map[ScopedKey[_], Compiled]
	type MapScoped = ScopedKey ~> ScopedKey

	def value[T](key: ScopedKey[T])(value: => T): Setting[T] = new Value(key, value _)
	def update[T](key: ScopedKey[T])(f: T => T): Setting[T] = app(key, key :^: KNil)(h => f(h.head))
	def app[HL <: HList, T](key: ScopedKey[T], inputs: KList[ScopedKey, HL])(f: HL => T): Setting[T] = new Apply(key, f, inputs)
	def uniform[S,T](key: ScopedKey[T], inputs: Seq[ScopedKey[S]])(f: Seq[S] => T): Setting[T] = new Uniform(key, f, inputs)
	def kapp[HL <: HList, M[_], T](key: ScopedKey[T], inputs: KList[({type l[t] = ScopedKey[M[t]]})#l, HL])(f: KList[M, HL] => T): Setting[T] = new KApply[HL, M, T](key, f, inputs)

	// the following is a temporary workaround for the "... cannot be instantiated from ..." bug, which renders 'kapp' above unusable outside this source file
	class KApp[HL <: HList, M[_], T] {
		type Composed[S] = ScopedKey[M[S]]
		def apply(key: ScopedKey[T], inputs: KList[Composed, HL])(f: KList[M, HL] => T): Setting[T] = new KApply[HL, M, T](key, f, inputs)
	}

	def empty(implicit delegates: Scope => Seq[Scope]): Settings[Scope] = new Settings0(Map.empty, delegates)
	def asTransform(s: Settings[Scope]): ScopedKey ~> Id = new (ScopedKey ~> Id) {
		def apply[T](k: ScopedKey[T]): T = getValue(s, k)
	}
	def getValue[T](s: Settings[Scope], k: ScopedKey[T]) = s.get(k.scope, k.key).get
	def asFunction[T](s: Settings[Scope]): ScopedKey[T] => T = k => getValue(s, k)

	def make(init: Seq[Setting[_]])(implicit delegates: Scope => Seq[Scope]): Settings[Scope] =
	{
		// group by Scope/Key, dropping dead initializations
		val sMap: ScopedMap = grouped(init)
		// delegate references to undefined values according to 'delegates'
		val dMap: ScopedMap = delegate(sMap)(delegates)
		// merge Seq[Setting[_]] into Compiled
		val cMap: CompiledMap = compile(dMap)
		// order the initializations.  cyclic references are detected here.
		val ordered: Seq[Compiled] = sort(cMap)
		// evaluation: apply the initializations.
		applyInits(ordered)
	}
	def sort(cMap: CompiledMap): Seq[Compiled] =
		Dag.topologicalSort(cMap.values)(_.dependencies.map(cMap))

	def compile(sMap: ScopedMap): CompiledMap =
		sMap.toSeq.map { case (k, ss) =>
			val deps = ss flatMap { _.dependsOn }
			val eval = (settings: Settings[Scope]) => (settings /: ss)(applySetting)
			(k, new Compiled(deps, eval))
		} toMap;

	def grouped(init: Seq[Setting[_]]): ScopedMap =
		((IMap.empty : ScopedMap) /: init) ( (m,s) => add(m,s) )

	def add[T](m: ScopedMap, s: Setting[T]): ScopedMap =
		m.mapValue[T]( s.key, Nil, ss => append(ss, s))

	def append[T](ss: Seq[Setting[T]], s: Setting[T]): Seq[Setting[T]] =
		if(s.definitive) s :: Nil else ss :+ s
		
	def delegate(sMap: ScopedMap)(implicit delegates: Scope => Seq[Scope]): ScopedMap =
	{
		val md = memoDelegates(delegates)
		def refMap(refKey: ScopedKey[_]) = new (ScopedKey ~> ScopedKey) { def apply[T](k: ScopedKey[T]) = delegateForKey(sMap, k, md(k.scope), refKey) }
		val f = new (SettingSeq ~> SettingSeq) { def apply[T](ks: Seq[Setting[T]]) = ks.map{ s => s mapReferenced refMap(s.key) } }
		sMap mapValues f
	}
	private[this] def delegateForKey[T](sMap: ScopedMap, k: ScopedKey[T], scopes: Seq[Scope], refKey: ScopedKey[_]): ScopedKey[T] = 
	{
		val scache = PMap.empty[ScopedKey, ScopedKey]
		def resolve(search: Seq[Scope]): ScopedKey[T] =
			search match {
				case Seq() => throw new Uninitialized(k)
				case Seq(x, xs @ _*) =>
					val sk = ScopedKey(x, k.key)
					scache.getOrUpdate(sk, if(defines(sMap, sk, refKey)) sk else resolve(xs))
			}
		resolve(scopes)
	}
	private[this] def defines(map: ScopedMap, key: ScopedKey[_], refKey: ScopedKey[_]): Boolean =
		(map get key) match { case Some(Seq(x, _*)) => (refKey != key) || x.definitive; case _ => false }
		
	private[this] def applyInits(ordered: Seq[Compiled])(implicit delegates: Scope => Seq[Scope]): Settings[Scope] =
		(empty /: ordered){ (m, comp) => comp.eval(m) }

	private[this] def memoDelegates(implicit delegates: Scope => Seq[Scope]): Scope => Seq[Scope] =
	{
		val dcache = new mutable.HashMap[Scope, Seq[Scope]]
		(scope: Scope) => dcache.getOrElseUpdate(scope, delegates(scope))
	}

	private[this] def applySetting[T](map: Settings[Scope], setting: Setting[T]): Settings[Scope] =
	{
		def execK[HL <: HList, M[_]](a: KApply[HL, M, T]) =
			map.set(a.key.scope, a.key.key, a.f(a.inputs.transform[M]( nestCon[ScopedKey, Id, M](asTransform(map)) )) )
		setting match
		{
			case s: Value[T] => map.set(s.key.scope, s.key.key, s.value())
			case u: Uniform[s, T] => map.set(u.key.scope, u.key.key, u.f(u.inputs map asFunction(map)) )
			case a: Apply[hl, T] => map.set(a.key.scope, a.key.key, a.f(a.inputs down asTransform(map) ) )
			case ka: KApply[hl, m, T] => execK[hl, m](ka) // separate method needed to workaround bug where m is not recognized as higher-kinded in inline version
		}
	}

	final class Uninitialized(key: ScopedKey[_]) extends Exception("Update on uninitialized setting " + key.key.label + " (in " + key.scope + ")")
	final class Compiled(val dependencies: Iterable[ScopedKey[_]], val eval: Settings[Scope] => Settings[Scope])

	sealed trait Setting[T]
	{
		def key: ScopedKey[T]
		def definitive: Boolean
		def dependsOn: Seq[ScopedKey[_]]
		def mapReferenced(g: MapScoped): Setting[T]
		def mapKey(g: MapScoped): Setting[T]
	}
	private[this] final class Value[T](val key: ScopedKey[T], val value: () => T) extends Setting[T]
	{
		def definitive = true
		def dependsOn = Nil
		def mapReferenced(g: MapScoped) = this
		def mapKey(g: MapScoped): Setting[T] = new Value(g(key), value)
	}
	private[this] final class Apply[HL <: HList, T](val key: ScopedKey[T], val f: HL => T, val inputs: KList[ScopedKey, HL]) extends Setting[T]
	{
		def definitive = !inputs.toList.contains(key)
		def dependsOn = remove(inputs.toList, key)
		def mapReferenced(g: MapScoped) = new Apply(key, f, inputs transform g)
		def mapKey(g: MapScoped): Setting[T] = new Apply(g(key), f, inputs)
	}
	private[this] final class KApply[HL <: HList, M[_], T](val key: ScopedKey[T], val f: KList[M, HL] => T, val inputs: KList[({type l[t] = ScopedKey[M[t]]})#l, HL]) extends Setting[T]
	{
		def definitive = !inputs.toList.contains(key)
		def dependsOn = remove(unnest(inputs.toList), key)
		def mapReferenced(g: MapScoped) = new KApply[HL, M, T](key, f, inputs.transform[({type l[t] = ScopedKey[M[t]]})#l]( nestCon(g) ) )
		def mapKey(g: MapScoped): Setting[T] = new KApply[HL, M, T](g(key), f, inputs)
		private[this] def unnest(l: List[ScopedKey[M[T]] forSome { type T }]): List[ScopedKey[_]] = l.asInstanceOf[List[ScopedKey[_]]]
	}
	private[this] final class Uniform[S, T](val key: ScopedKey[T], val f: Seq[S] => T, val inputs: Seq[ScopedKey[S]]) extends Setting[T]
	{
		def definitive = !inputs.contains(key)
		def dependsOn = remove(inputs, key)
		def mapReferenced(g: MapScoped) = new Uniform(key, f, inputs map g.fn[S])
		def mapKey(g: MapScoped): Setting[T] = new Uniform(g(key), f, inputs)
	}
	private def remove[T](s: Seq[T], v: T) = s filterNot (_ == v)
}
