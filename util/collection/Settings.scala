/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Types._

sealed trait Settings[Scope]
{
	def data: Map[Scope, AttributeMap]
	def keys(scope: Scope): Set[AttributeKey[_]]
	def scopes: Set[Scope]
	def definingScope(scope: Scope, key: AttributeKey[_]): Option[Scope]
	def allKeys[T](f: (Scope, AttributeKey[_]) => T): Seq[T]
	def get[T](scope: Scope, key: AttributeKey[T]): Option[T]
	def set[T](scope: Scope, key: AttributeKey[T], value: T): Settings[Scope]
}

private final class Settings0[Scope](val data: Map[Scope, AttributeMap], val delegates: Scope => Seq[Scope]) extends Settings[Scope]
{
	def scopes: Set[Scope] = data.keySet.toSet
	def keys(scope: Scope) = data(scope).keys.toSet
	def allKeys[T](f: (Scope, AttributeKey[_]) => T): Seq[T] = data.flatMap { case (scope, map) => map.keys.map(k => f(scope, k)) } toSeq;

	def get[T](scope: Scope, key: AttributeKey[T]): Option[T] =
		delegates(scope).toStream.flatMap(sc => scopeLocal(sc, key) ).headOption
	def definingScope(scope: Scope, key: AttributeKey[_]): Option[Scope] =
		delegates(scope).toStream.filter(sc => scopeLocal(sc, key).isDefined ).headOption

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
	type ScopeLocal = ScopedKey[_] => Seq[Setting[_]]

	def setting[T](key: ScopedKey[T], init: Initialize[T]): Setting[T] = new Setting[T](key, init)
	def value[T](value: => T): Initialize[T] = new Value(value _)
	def update[T](key: ScopedKey[T])(f: T => T): Setting[T] = new Setting[T](key, app(key :^: KNil)(hl => f(hl.head)))
	def app[HL <: HList, T](inputs: KList[ScopedKey, HL])(f: HL => T): Initialize[T] = new Apply(f, inputs)
	def uniform[S,T](inputs: Seq[ScopedKey[S]])(f: Seq[S] => T): Initialize[T] = new Uniform(f, inputs)
	def kapp[HL <: HList, M[_], T](inputs: KList[({type l[t] = ScopedKey[M[t]]})#l, HL])(f: KList[M, HL] => T): Initialize[T] = new KApply[HL, M, T](f, inputs)

	// the following is a temporary workaround for the "... cannot be instantiated from ..." bug, which renders 'kapp' above unusable outside this source file
	class KApp[HL <: HList, M[_], T] {
		type Composed[S] = ScopedKey[M[S]]
		def apply(inputs: KList[Composed, HL])(f: KList[M, HL] => T): Initialize[T] = new KApply[HL, M, T](f, inputs)
	}

	def empty(implicit delegates: Scope => Seq[Scope]): Settings[Scope] = new Settings0(Map.empty, delegates)
	def asTransform(s: Settings[Scope]): ScopedKey ~> Id = new (ScopedKey ~> Id) {
		def apply[T](k: ScopedKey[T]): T = getValue(s, k)
	}
	def getValue[T](s: Settings[Scope], k: ScopedKey[T]) = s.get(k.scope, k.key).get
	def asFunction[T](s: Settings[Scope]): ScopedKey[T] => T = k => getValue(s, k)

	def compiled(init: Seq[Setting[_]])(implicit delegates: Scope => Seq[Scope], scopeLocal: ScopeLocal): CompiledMap =
	{
		// prepend per-scope settings 
		val withLocal = addLocal(init)(scopeLocal)
		// group by Scope/Key, dropping dead initializations
		val sMap: ScopedMap = grouped(withLocal)
		// delegate references to undefined values according to 'delegates'
		val dMap: ScopedMap = delegate(sMap)(delegates)
		// merge Seq[Setting[_]] into Compiled
		compile(dMap)
	}
	def make(init: Seq[Setting[_]])(implicit delegates: Scope => Seq[Scope], scopeLocal: ScopeLocal): Settings[Scope] =
	{
		val cMap = compiled(init)(delegates, scopeLocal)
		// order the initializations.  cyclic references are detected here.
		val ordered: Seq[Compiled] = sort(cMap)
		// evaluation: apply the initializations.
		applyInits(ordered)
	}
	def sort(cMap: CompiledMap): Seq[Compiled] =
		Dag.topologicalSort(cMap.values)(_.dependencies.map(cMap))

	def compile(sMap: ScopedMap): CompiledMap =
		sMap.toSeq.map { case (k, ss) =>
			val deps = ss flatMap { _.dependsOn } toSet;
			val eval = (settings: Settings[Scope]) => (settings /: ss)(applySetting)
			(k, new Compiled(k, deps, eval))
		} toMap;

	def grouped(init: Seq[Setting[_]]): ScopedMap =
		((IMap.empty : ScopedMap) /: init) ( (m,s) => add(m,s) )

	def add[T](m: ScopedMap, s: Setting[T]): ScopedMap =
		m.mapValue[T]( s.key, Nil, ss => append(ss, s))

	def append[T](ss: Seq[Setting[T]], s: Setting[T]): Seq[Setting[T]] =
		if(s.definitive) s :: Nil else ss :+ s

	def addLocal(init: Seq[Setting[_]])(implicit scopeLocal: ScopeLocal): Seq[Setting[_]] =
		init.flatMap( _.dependsOn flatMap scopeLocal )  ++  init
		
	def delegate(sMap: ScopedMap)(implicit delegates: Scope => Seq[Scope]): ScopedMap =
	{
		def refMap(refKey: ScopedKey[_], isFirst: Boolean) = new (ScopedKey ~> ScopedKey) { def apply[T](k: ScopedKey[T]) =
			delegateForKey(sMap, k, delegates(k.scope), refKey, isFirst)
		}
		val f = new (SettingSeq ~> SettingSeq) { def apply[T](ks: Seq[Setting[T]]) =
			ks.zipWithIndex.map{ case (s,i) => s mapReferenced refMap(s.key, i == 0) }
		}
		sMap mapValues f
	}
	private[this] def delegateForKey[T](sMap: ScopedMap, k: ScopedKey[T], scopes: Seq[Scope], refKey: ScopedKey[_], isFirst: Boolean): ScopedKey[T] = 
	{
		val scache = PMap.empty[ScopedKey, ScopedKey]
		def resolve(search: Seq[Scope]): ScopedKey[T] =
			search match {
				case Seq() => throw Uninitialized(k, refKey)
				case Seq(x, xs @ _*) =>
					val sk = ScopedKey(x, k.key)
					scache.getOrUpdate(sk, if(defines(sMap, sk, refKey, isFirst)) sk else resolve(xs))
			}
		resolve(scopes)
	}
	private[this] def defines(map: ScopedMap, key: ScopedKey[_], refKey: ScopedKey[_], isFirst: Boolean): Boolean =
		(map get key) match { case Some(Seq(x, _*)) => (refKey != key) || !isFirst; case _ => false }
		
	private[this] def applyInits(ordered: Seq[Compiled])(implicit delegates: Scope => Seq[Scope]): Settings[Scope] =
		(empty /: ordered){ (m, comp) => comp.eval(m) }

	private[this] def applySetting[T](map: Settings[Scope], setting: Setting[T]): Settings[Scope] =
	{
		val value = setting.init.get(map)
		val key = setting.key
		map.set(key.scope, key.key, value)
	}

	final class Uninitialized(val key: ScopedKey[_], val refKey: ScopedKey[_], msg: String) extends Exception(msg)
	def Uninitialized(key: ScopedKey[_], refKey: ScopedKey[_]): Uninitialized =
		new Uninitialized(key, refKey, "Reference to uninitialized setting " + key.key.label + " (in " + key.scope + ") from " + refKey.key.label +" (in " + refKey.scope + ")")
	final class Compiled(val key: ScopedKey[_], val dependencies: Iterable[ScopedKey[_]], val eval: Settings[Scope] => Settings[Scope])

	sealed trait Initialize[T]
	{
		def dependsOn: Seq[ScopedKey[_]]
		def map[S](g: T => S): Initialize[S]
		def mapReferenced(g: MapScoped): Initialize[T]
		def zip[S](o: Initialize[S]): Initialize[(T,S)] = zipWith(o)((x,y) => (x,y))
		def zipWith[S,U](o: Initialize[S])(f: (T,S) => U): Initialize[U] = new Joined[T,S,U](this, o, f)
		def get(map: Settings[Scope]): T
	}
	object Initialize
	{
		implicit def joinInitialize[T](s: Seq[Initialize[T]]): JoinInitSeq[T] = new JoinInitSeq(s)
		final class JoinInitSeq[T](s: Seq[Initialize[T]])
		{
			def join[S](f: Seq[T] => S): Initialize[S] = this.join map f
			def join: Initialize[Seq[T]] = Initialize.join(s)
		}
		def join[T](inits: Seq[Initialize[T]]): Initialize[Seq[T]] =
			inits match
			{
				case Seq() => value( Nil )
				case Seq(x, xs @ _*) => (join(xs) zipWith x)( (t,h) => h +: t)
			}
	}
	final class Setting[T](val key: ScopedKey[T], val init: Initialize[T])
	{
		def definitive: Boolean = !init.dependsOn.contains(key)
		def dependsOn: Seq[ScopedKey[_]] = remove(init.dependsOn, key)
		def mapReferenced(g: MapScoped): Setting[T] = new Setting(key, init mapReferenced g)
		def mapKey(g: MapScoped): Setting[T] = new Setting(g(key), init)
		def mapInit(f: (ScopedKey[T], T) => T): Setting[T] = new Setting(key, init.map(t => f(key,t)))
		override def toString = "setting(" + key + ")"
	}

	private[this] final class Joined[S,T,U](a: Initialize[S], b: Initialize[T], f: (S,T) => U) extends Initialize[U]
	{
		def dependsOn = a.dependsOn ++ b.dependsOn
		def mapReferenced(g: MapScoped) = new Joined(a mapReferenced g, b mapReferenced g, f)
		def map[Z](g: U => Z) = new Joined[S,T,Z](a, b, (s,t) => g(f(s,t)))
		def get(map: Settings[Scope]): U = f(a get map, b get map)
	}
	private[this] final class Value[T](value: () => T) extends Initialize[T]
	{
		def dependsOn = Nil
		def mapReferenced(g: MapScoped) = this
		def map[S](g: T => S) = new Value[S](() => g(value()))
		def get(map: Settings[Scope]): T = value()
	}
	private[this] final class Apply[HL <: HList, T](val f: HL => T, val inputs: KList[ScopedKey, HL]) extends Initialize[T]
	{
		def dependsOn = inputs.toList
		def mapReferenced(g: MapScoped) = new Apply(f, inputs transform g)
		def map[S](g: T => S) = new Apply(g compose f, inputs)
		def get(map: Settings[Scope]) = f(inputs down asTransform(map) )
	}
	private[this] final class KApply[HL <: HList, M[_], T](val f: KList[M, HL] => T, val inputs: KList[({type l[t] = ScopedKey[M[t]]})#l, HL]) extends Initialize[T]
	{
		def dependsOn = unnest(inputs.toList)
		def mapReferenced(g: MapScoped) = new KApply[HL, M, T](f, inputs.transform[({type l[t] = ScopedKey[M[t]]})#l]( nestCon(g) ) )
		def map[S](g: T => S) = new KApply[HL, M, S](g compose f, inputs)
		def get(map: Settings[Scope]) = f(inputs.transform[M]( nestCon[ScopedKey, Id, M](asTransform(map)) ))
		private[this] def unnest(l: List[ScopedKey[M[T]] forSome { type T }]): List[ScopedKey[_]] = l.asInstanceOf[List[ScopedKey[_]]]
	}
	private[this] final class Uniform[S, T](val f: Seq[S] => T, val inputs: Seq[ScopedKey[S]]) extends Initialize[T]
	{
		def dependsOn = inputs
		def mapReferenced(g: MapScoped) = new Uniform(f, inputs map g.fn[S])
		def map[S](g: T => S) = new Uniform(g compose f, inputs)
		def get(map: Settings[Scope]) = f(inputs map asFunction(map))
	}
	private def remove[T](s: Seq[T], v: T) = s filterNot (_ == v)
}
