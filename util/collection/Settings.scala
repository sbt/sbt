package sbt

	import Types._
	import annotation.tailrec
	import collection.mutable

sealed trait Settings[Scope]
{
	def data: Scope => AttributeMap
	def scopes: Seq[Scope]
	def get[T](scope: Scope, key: AttributeKey[T]): Option[T]
	def set[T](scope: Scope, key: AttributeKey[T], value: T): Settings[Scope]
}

private final class Settings0[Scope](val data: Map[Scope, AttributeMap], val delegates: Scope => Seq[Scope]) extends Settings[Scope]
{
	def scopes: Seq[Scope] = data.keys.toSeq

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
final class Init[Scope](val delegates: Scope => Seq[Scope])
{
	final case class ScopedKey[T](scope: Scope, key: AttributeKey[T])

	type SettingSeq[T] = Seq[Setting[T]]
	type ScopedMap = IMap[ScopedKey, SettingSeq]
	type CompiledMap = Map[ScopedKey[_], Compiled]
	type MapScoped = ScopedKey ~> ScopedKey

	def value[T](key: ScopedKey[T])(value: => T): Setting[T] = new Value(key, value _)
	def update[T](key: ScopedKey[T])(f: T => T): Setting[T] = app(key, key :^: KNil)(h => f(h.head))
	def app[HL <: HList, T](key: ScopedKey[T], inputs: KList[ScopedKey, HL])(f: HL => T): Setting[T] = new Apply(key, f, inputs)

	def empty: Settings[Scope] = new Settings0(Map.empty, delegates)
	def asTransform(s: Settings[Scope]): ScopedKey ~> Id = new (ScopedKey ~> Id) {
		def apply[T](k: ScopedKey[T]): T = s.get(k.scope, k.key).get
	}

	def make(init: Seq[Setting[_]]): Settings[Scope] =
	{
		// group by Scope/Key, dropping dead initializations
		val sMap: ScopedMap = grouped(init)
		// delegate references to undefined values according to 'delegates'
		val dMap: ScopedMap = delegate(sMap)
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
		
	def delegate(sMap: ScopedMap): ScopedMap =
	{
		val md = memoDelegates
		def refMap(refKey: ScopedKey[_]) = new (ScopedKey ~> ScopedKey) { def apply[T](k: ScopedKey[T]) = mapReferenced(sMap, k, md(k.scope), refKey) }
		val f = new (SettingSeq ~> SettingSeq) { def apply[T](ks: Seq[Setting[T]]) = ks.map{ s => s mapReferenced refMap(s.key) } }
		sMap mapValues f
	}
	private[this] def mapReferenced[T](sMap: ScopedMap, k: ScopedKey[T], scopes: Seq[Scope], refKey: ScopedKey[_]): ScopedKey[T] = 
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
		
	private[this] def applyInits(ordered: Seq[Compiled]): Settings[Scope] =
		(empty /: ordered){ (m, comp) => comp.eval(m) }

	private[this] def memoDelegates: Scope => Seq[Scope] =
	{
		val dcache = new mutable.HashMap[Scope, Seq[Scope]]
		(scope: Scope) => dcache.getOrElseUpdate(scope, delegates(scope))
	}

	private[this] def applySetting[T](map: Settings[Scope], a: Setting[T]): Settings[Scope] =
		a match
		{
			case s: Value[T] => map.set(s.key.scope, s.key.key, s.value())
			case a: Apply[hl, T] => map.set(a.key.scope, a.key.key, a.f(a.inputs.down(asTransform(map))))
		}

	final class Uninitialized(key: ScopedKey[_]) extends Exception("Update on uninitialized setting " + key.key.label + " (in " + key.scope + ")")
	final class Compiled(val dependencies: Iterable[ScopedKey[_]], val eval: Settings[Scope] => Settings[Scope])

	sealed trait Setting[T]
	{
		def key: ScopedKey[T]
		def definitive: Boolean
		def dependsOn: Seq[ScopedKey[_]]
		def mapReferenced(f: MapScoped): Setting[T]
	}
	private[this] final class Value[T](val key: ScopedKey[T], val value: () => T) extends Setting[T]
	{
		def definitive = true
		def dependsOn = Nil
		def mapReferenced(f: MapScoped) = this
	}
	private[this] final class Apply[HL <: HList, T](val key: ScopedKey[T], val f: HL => T, val inputs: KList[ScopedKey, HL]) extends Setting[T]
	{
		def definitive = !inputs.toList.contains(key)
		def dependsOn = inputs.toList - key
		def mapReferenced(g: MapScoped) = new Apply(key, f, inputs transform g)
	}
}
