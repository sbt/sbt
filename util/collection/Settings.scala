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
	def display(skey: ScopedKey[_]): String

	final case class ScopedKey[T](scope: Scope, key: AttributeKey[T])

	type SettingSeq[T] = Seq[Setting[T]]
	type ScopedMap = IMap[ScopedKey, SettingSeq]
	type CompiledMap = Map[ScopedKey[_], Compiled]
	type MapScoped = ScopedKey ~> ScopedKey
	type ValidatedRef[T] = Either[Undefined, ScopedKey[T]]
	type ValidateRef = ScopedKey ~> ValidatedRef
	type ScopeLocal = ScopedKey[_] => Seq[Setting[_]]
	type MapConstant = ScopedKey ~> Option

	def setting[T](key: ScopedKey[T], init: Initialize[T]): Setting[T] = new Setting[T](key, init)
	def value[T](value: => T): Initialize[T] = new Value(value _)
	def optional[T,U](key: ScopedKey[T])(f: Option[T] => U): Initialize[U] = new Optional(Some(key), f)
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
	def getValue[T](s: Settings[Scope], k: ScopedKey[T]) = s.get(k.scope, k.key) getOrElse error("Internal settings error: invalid reference to " + display(k))
	def asFunction[T](s: Settings[Scope]): ScopedKey[T] => T = k => getValue(s, k)

	def compiled(init: Seq[Setting[_]], actual: Boolean = true)(implicit delegates: Scope => Seq[Scope], scopeLocal: ScopeLocal): CompiledMap =
	{
		// prepend per-scope settings 
		val withLocal = addLocal(init)(scopeLocal)
		// group by Scope/Key, dropping dead initializations
		val sMap: ScopedMap = grouped(withLocal)
		// delegate references to undefined values according to 'delegates'
		val dMap: ScopedMap = if(actual) delegate(sMap)(delegates) else sMap
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
		def refMap(refKey: ScopedKey[_], isFirst: Boolean) = new ValidateRef { def apply[T](k: ScopedKey[T]) =
			delegateForKey(sMap, k, delegates(k.scope), refKey, isFirst)
		}
		val undefineds = new collection.mutable.ListBuffer[Undefined]
		val f = new (SettingSeq ~> SettingSeq) { def apply[T](ks: Seq[Setting[T]]) =
			ks.zipWithIndex.map{ case (s,i) =>
				(s validateReferenced refMap(s.key, i == 0) ) match {
					case Right(v) => v
					case Left(l) => undefineds ++= l; s
				}
			}
		}
		val result = sMap mapValues f
		if(undefineds.isEmpty) result else throw Uninitialized(undefineds.toList)
	}
	private[this] def delegateForKey[T](sMap: ScopedMap, k: ScopedKey[T], scopes: Seq[Scope], refKey: ScopedKey[_], isFirst: Boolean): Either[Undefined, ScopedKey[T]] = 
	{
		def resolve(search: Seq[Scope]): Either[Undefined, ScopedKey[T]] =
			search match {
				case Seq() => Left(Undefined(refKey, k))
				case Seq(x, xs @ _*) =>
					val sk = ScopedKey(x, k.key)
					val definesKey = (refKey != sk || !isFirst) && (sMap contains sk)
					if(definesKey) Right(sk) else resolve(xs)
			}
		resolve(scopes)
	}
		
	private[this] def applyInits(ordered: Seq[Compiled])(implicit delegates: Scope => Seq[Scope]): Settings[Scope] =
		(empty /: ordered){ (m, comp) => comp.eval(m) }

	private[this] def applySetting[T](map: Settings[Scope], setting: Setting[T]): Settings[Scope] =
	{
		val value = setting.init.get(map)
		val key = setting.key
		map.set(key.scope, key.key, value)
	}

	final class Uninitialized(val undefined: Seq[Undefined], msg: String) extends Exception(msg)
	final class Undefined(val definingKey: ScopedKey[_], val referencedKey: ScopedKey[_])
	def Undefined(definingKey: ScopedKey[_], referencedKey: ScopedKey[_]): Undefined = new Undefined(definingKey, referencedKey)
	def Uninitialized(keys: Seq[Undefined]): Uninitialized =
	{
		assert(!keys.isEmpty)
		val keyStrings = keys map { u => display(u.referencedKey) + " from " + display(u.definingKey) }
		val suffix = if(keyStrings.length > 1) "s" else ""
		val keysString = keyStrings.mkString("\n\t", "\n\t", "")
		new Uninitialized(keys, "Reference" + suffix + " to undefined setting" + suffix + ": " + keysString)
	}
	final class Compiled(val key: ScopedKey[_], val dependencies: Iterable[ScopedKey[_]], val eval: Settings[Scope] => Settings[Scope])
	{
		override def toString = display(key)
	}

	sealed trait Initialize[T]
	{
		def dependsOn: Seq[ScopedKey[_]]
		def map[S](g: T => S): Initialize[S]
		def mapReferenced(g: MapScoped): Initialize[T]
		def validateReferenced(g: ValidateRef): Either[Seq[Undefined], Initialize[T]]
		def zip[S](o: Initialize[S]): Initialize[(T,S)] = zipWith(o)((x,y) => (x,y))
		def zipWith[S,U](o: Initialize[S])(f: (T,S) => U): Initialize[U] = new Joined[T,S,U](this, o, f)
		def mapConstant(g: MapConstant): Initialize[T]
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
	sealed trait SettingsDefinition {
		def settings: Seq[Setting[_]]
	}
	final class SettingList(val settings: Seq[Setting[_]]) extends SettingsDefinition
	final class Setting[T](val key: ScopedKey[T], val init: Initialize[T]) extends SettingsDefinition
	{
		def settings = this :: Nil
		def definitive: Boolean = !init.dependsOn.contains(key)
		def dependsOn: Seq[ScopedKey[_]] = remove(init.dependsOn, key)
		def mapReferenced(g: MapScoped): Setting[T] = new Setting(key, init mapReferenced g)
		def validateReferenced(g: ValidateRef): Either[Seq[Undefined], Setting[T]] = (init validateReferenced g).right.map(newI => new Setting(key, newI))
		def mapKey(g: MapScoped): Setting[T] = new Setting(g(key), init)
		def mapInit(f: (ScopedKey[T], T) => T): Setting[T] = new Setting(key, init.map(t => f(key,t)))
		def mapConstant(g: MapConstant): Setting[T] = new Setting(key, init mapConstant g)
		override def toString = "setting(" + key + ")"
	}

	private[this] final class Optional[S,T](a: Option[ScopedKey[S]], f: Option[S] => T) extends Initialize[T]
	{
		def dependsOn = a.toList
		def map[Z](g: T => Z): Initialize[Z] = new Optional[S,Z](a, g compose f)
		def get(map: Settings[Scope]): T = f(a map asFunction(map))
		def mapReferenced(g: MapScoped) = new Optional(a map g.fn, f)
		def validateReferenced(g: ValidateRef) = Right( new Optional(a flatMap { sk => g(sk).right.toOption }, f) )
		def mapConstant(g: MapConstant): Initialize[T] =
			(a flatMap g.fn) match {
				case None => this
				case s => new Value(() => f(s))
			}
	}
	private[this] final class Joined[S,T,U](a: Initialize[S], b: Initialize[T], f: (S,T) => U) extends Initialize[U]
	{
		def dependsOn = a.dependsOn ++ b.dependsOn
		def mapReferenced(g: MapScoped) = new Joined(a mapReferenced g, b mapReferenced g, f)
		def validateReferenced(g: ValidateRef) =
			(a validateReferenced g, b validateReferenced g) match {
				case (Right(ak), Right(bk)) => Right( new Joined(ak, bk, f) )
				case (au, bu) => Left( (au.left.toSeq ++ bu.left.toSeq).flatten )
			}
		def map[Z](g: U => Z) = new Joined[S,T,Z](a, b, (s,t) => g(f(s,t)))
		def mapConstant(g: MapConstant) = new Joined[S,T,U](a mapConstant g, b mapConstant g, f)
		def get(map: Settings[Scope]): U = f(a get map, b get map)
	}
	private[this] final class Value[T](value: () => T) extends Initialize[T]
	{
		def dependsOn = Nil
		def mapReferenced(g: MapScoped) = this
		def validateReferenced(g: ValidateRef) = Right(this)
		def map[S](g: T => S) = new Value[S](() => g(value()))
		def mapConstant(g: MapConstant) = this
		def get(map: Settings[Scope]): T = value()
	}
	private[this] final class Apply[HL <: HList, T](val f: HL => T, val inputs: KList[ScopedKey, HL]) extends Initialize[T]
	{
		def dependsOn = inputs.toList
		def mapReferenced(g: MapScoped) = new Apply(f, inputs transform g)
		def map[S](g: T => S) = new Apply(g compose f, inputs)
		def mapConstant(g: MapConstant) = Reduced.reduceH(inputs, g).combine( (keys, expand) => new Apply(f compose expand, keys) )
		def get(map: Settings[Scope]) = f(inputs down asTransform(map) )
		def validateReferenced(g: ValidateRef) =
		{
			val tx = inputs.transform(g)
			val undefs = tx.toList.flatMap(_.left.toSeq)
			val get = new (ValidatedRef ~> ScopedKey) { def apply[T](vr: ValidatedRef[T]) = vr.right.get }
			if(undefs.isEmpty) Right(new Apply(f, tx transform get)) else Left(undefs)
		}
	}

	private[this] final class KApply[HL <: HList, M[_], T](val f: KList[M, HL] => T, val inputs: KList[({type l[t] = ScopedKey[M[t]]})#l, HL]) extends Initialize[T]
	{
		type ScopedKeyM[T] = ScopedKey[M[T]]
		type VRefM[T] = ValidatedRef[M[T]]
		def dependsOn = unnest(inputs.toList)
		def mapReferenced(g: MapScoped) = new KApply[HL, M, T](f, inputs.transform[({type l[t] = ScopedKey[M[t]]})#l]( nestCon(g) ) )
		def map[S](g: T => S) = new KApply[HL, M, S](g compose f, inputs)
		def get(map: Settings[Scope]) = f(inputs.transform[M]( nestCon[ScopedKey, Id, M](asTransform(map)) ))
		def mapConstant(g: MapConstant) =
		{
			def mk[HLk <: HList](keys: KList[ScopedKeyM, HLk], expand: KList[M, HLk] => KList[M, HL]) = new KApply[HLk, M, T](f compose expand, keys)
			Reduced.reduceK[HL, ScopedKey, M](inputs, nestCon(g)) combine mk
		}
		def validateReferenced(g: ValidateRef) =
		{
			val tx = inputs.transform[VRefM](nestCon(g))
			val undefs = tx.toList.flatMap(_.left.toSeq)
			val get = new (VRefM ~> ScopedKeyM) { def apply[T](vr: ValidatedRef[M[T]]) = vr.right.get }
			if(undefs.isEmpty)
				Right(new KApply[HL, M, T](f, tx.transform( get ) ))
			else
				Left(undefs)
		}
		private[this] def unnest(l: List[ScopedKey[M[T]] forSome { type T }]): List[ScopedKey[_]] = l.asInstanceOf[List[ScopedKey[_]]]
	}
	private[this] final class Uniform[S, T](val f: Seq[S] => T, val inputs: Seq[ScopedKey[S]]) extends Initialize[T]
	{
		def dependsOn = inputs
		def mapReferenced(g: MapScoped) = new Uniform(f, inputs map g.fn[S])
		def validateReferenced(g: ValidateRef) =
		{
			val (undefs, ok) = List.separate(inputs map g.fn[S])
			if(undefs.isEmpty) Right( new Uniform(f, ok) ) else Left(undefs)
		}
		def map[S](g: T => S) = new Uniform(g compose f, inputs)
		def mapConstant(g: MapConstant) =
		{
			val red = Reduced.reduceSeq(inputs, g)
			new Uniform(f compose red.expand, red.keys)
		}
		def get(map: Settings[Scope]) = f(inputs map asFunction(map))
	}
	private def remove[T](s: Seq[T], v: T) = s filterNot (_ == v)
}
