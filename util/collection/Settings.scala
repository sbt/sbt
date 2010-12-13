package sbt

	import annotation.tailrec
	import Settings._

sealed trait Settings[Scope]
{
	def data: Scope => AttributeMap
	def definitions: Scope => Definitions
	def linear: Scope => Seq[Scope]
	def get[T](scope: Scope, key: AttributeKey[T]): Option[T]
	def set[T](scope: Scope, key: AttributeKey[T], value: T): Settings[Scope]
}
private final class Settings0[Scope](val data: Map[Scope, AttributeMap], val definitions: Map[Scope, Definitions], val linear: Scope => Seq[Scope]) extends Settings[Scope]
{
	def get[T](scope: Scope, key: AttributeKey[T]): Option[T] =
		linear(scope).toStream.flatMap(sc => scopeLocal(sc, key) ).headOption

	private def scopeLocal[T](scope: Scope, key: AttributeKey[T]): Option[T] =
		(data get scope).flatMap(_ get key)

	def set[T](scope: Scope, key: AttributeKey[T], value: T): Settings[Scope] =
	{
		val map = (data get scope) getOrElse AttributeMap.empty
		val newData = data.updated(scope, map.put(key, value))
		new Settings0(newData, definitions, linear)
	}
}
object Settings
{
	type Data[Scope] = Map[Scope, AttributeMap]
	type Init = Seq[Setting[_]]
	type Keys = Set[AttributeKey[_]]

	def make[Scope](inits: Iterable[(Scope,Init)], lzA: Scope => Seq[Scope]): Settings[Scope] =
	{
		val definitions = inits map { case (scope, init)  =>  (scope, compile(init)) } toMap;
		val resolved = for( (scope, definition) <- definitions) yield (scope, resolveScopes(definition, lzA(scope), definitions) )
		val scopeDeps = resolved map { case (scope, requiredMap)  =>  (scope, requiredMap.values) } toMap;
		val ordered = Dag.topologicalSort(scopeDeps.keys)(scopeDeps)
		val data = (Map.empty[Scope, AttributeMap] /: ordered) { (mp, scope) => add(mp, scope, definitions, resolved) }
		new Settings0(data, definitions, lzA)
	}

	private[this] def add[Scope](data: Data[Scope], scope: Scope, definitions: Map[Scope, Definitions], resolved: Map[Scope, Map[AttributeKey[_], Scope]]): Map[Scope, AttributeMap] =
		data.updated(scope, mkScopeMap(data, definitions(scope), resolved(scope)) )

	private[this] def mkScopeMap[Scope](data: Data[Scope], definitions: Definitions, definedIn: Map[AttributeKey[_], Scope]): AttributeMap =
	{
		val start = (AttributeMap.empty /: definitions.requires) ( (mp, key) => prepop(data, definedIn, mp, key))
		definitions eval start
	}

	private[this] def prepop[T, Scope](data: Data[Scope], definedIn: Map[AttributeKey[_], Scope], mp: AttributeMap, key: AttributeKey[T]): AttributeMap =
		mp.put(key, data(definedIn(key))(key))	
		
	private[this] def resolveScopes[Scope](definition: Definitions, search: Seq[Scope], definitions: Map[Scope, Definitions]): Map[AttributeKey[_], Scope]  =
		definition.requires.view.map(req => (req, resolveScope(req, search, definitions )) ).toMap

	private[this] def resolveScope[Scope](key: AttributeKey[_], search: Seq[Scope], definitions: Map[Scope, Definitions]): Scope =
		search find defines(key, definitions) getOrElse { throw new Uninitialized(key) }
	
	private[this] def defines[Scope](key: AttributeKey[_], definitions: Map[Scope, Definitions])(scope: Scope): Boolean =
		(definitions get scope).filter(_.provides contains key).isDefined
	
	final class Definitions(val provides: Keys, val requires: Keys, val eval: AttributeMap => AttributeMap)

	def value[T](key: AttributeKey[T])(value: => T): Setting[T] = new Value(key, value _)
	def update[T](key: AttributeKey[T])(f: T => T): Setting[T] = new Update(key, f)
	def app[HL <: HList, T](key: AttributeKey[T], inputs: KList[AttributeKey, HL])(f: HL => T): Setting[T] = new Apply(key, f, inputs)

	def compile(settings: Seq[Setting[_]]): Definitions =
	{
		val grpd = grouped(settings)
		val sorted = sort(grpd)
		val eval = (map: AttributeMap) => (map /: sorted)( (m, c) => c eval m )
		val provided = grpd.keySet.toSet
		val requires = sorted.flatMap(_.dependencies).toSet -- provided ++ sorted.collect { case c if !c.selfContained => c.key }
		new Definitions(provided, requires, eval)
	}
	private[this] def grouped(settings: Seq[Setting[_]]): Map[AttributeKey[_], Compiled] =
		settings.groupBy(_.key) map { case (key: AttributeKey[t], actions) =>
			(key: AttributeKey[_], compileSetting(key, actions.asInstanceOf[Seq[Setting[t]]]) )
		} toMap;

	private[this] def compileSetting[T](key: AttributeKey[T], actions: Seq[Setting[T]]): Compiled =
	{
		val (alive, selfContained) = live(key, actions)
		val f = (map: AttributeMap) => (map /: alive)(eval)
		new Compiled(key, f, dependencies(actions), selfContained)
	}
	private[this] final class Compiled(val key: AttributeKey[_], val eval: AttributeMap => AttributeMap, val dependencies: Iterable[AttributeKey[_]], val selfContained: Boolean) {
		override def toString = key.label
	}

	private[this] def sort(actionMap: Map[AttributeKey[_], Compiled]): Seq[Compiled] =
		Dag.topologicalSort(actionMap.values)( _.dependencies.flatMap(actionMap.get) )

	private[this] def live[T](key: AttributeKey[T], actions: Seq[Setting[T]]): (Seq[Setting[T]], Boolean) =
	{
		val lastOverwrite = actions.lastIndexWhere(_ overwrite key)
		val selfContained = lastOverwrite >= 0
		val alive = if(selfContained) actions.drop(lastOverwrite) else actions
		(alive, selfContained)
	}
	private[this] def dependencies(actions: Seq[Setting[_]]): Seq[AttributeKey[_]]  =  actions.flatMap(_.dependsOn)
	private[this] def eval[T](map: AttributeMap, a: Setting[T]): AttributeMap =
		a match
		{
			case s: Value[T] => map.put(s.key, s.value())
			case u: Update[T] => map.put(u.key, u.f(map(u.key)))
			case a: Apply[hl, T] => map.put(a.key, a.f(a.inputs.down(map)))
		}

	sealed trait Setting[T]
	{
		def key: AttributeKey[T]
		def overwrite(key: AttributeKey[T]): Boolean
		def dependsOn: Seq[AttributeKey[_]] = Nil
	}
	private[this] final class Value[T](val key: AttributeKey[T], val value: () => T) extends Setting[T]
	{
		def overwrite(key: AttributeKey[T]) = true
	}
	private[this] final class Update[T](val key: AttributeKey[T], val f: T => T) extends Setting[T]
	{
		def overwrite(key: AttributeKey[T]) = false
	}
	private[this] final class Apply[HL <: HList, T](val key: AttributeKey[T], val f: HL => T, val inputs: KList[AttributeKey, HL]) extends Setting[T]
	{
		def overwrite(key: AttributeKey[T]) = inputs.toList.forall(_ ne key)
		override def dependsOn = inputs.toList - key
	}

	final class Uninitialized(key: AttributeKey[_]) extends Exception("Update on uninitialized setting " + key.label)
}
