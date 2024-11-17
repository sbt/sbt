/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import Types.*
import sbt.util.Show
import Util.{ nil, nilSeq }
import scala.jdk.CollectionConverters.*

// delegates should contain the input ScopeType as the first entry
// this trait is intended to be mixed into an object
trait Init:
  type ScopeType

  /**
   * The Show instance used when a detailed String needs to be generated.
   *  It is typically used when no context is available.
   */
  def showFullKey: Show[ScopedKey[?]]

  sealed case class ScopedKey[A](scope: ScopeType, key: AttributeKey[A]) extends KeyedInitialize[A]:
    def scopedKey = this
  end ScopedKey

  type SettingSeq[A] = Seq[Setting[A]]
  type ScopedMap = IMap[ScopedKey, SettingSeq]
  type CompiledMap = Map[ScopedKey[?], Compiled[?]]
  type MapScoped = [a] => ScopedKey[a] => ScopedKey[a]
  type ValidatedRef[A] = Either[Undefined, ScopedKey[A]]
  type ValidatedInit[A] = Either[Seq[Undefined], Initialize[A]]
  type ValidateRef = [a] => ScopedKey[a] => ValidatedRef[a]
  type ScopeLocal = ScopedKey[?] => Seq[Setting[?]]
  type MapConstant = [a] => ScopedKey[a] => Option[a]

  sealed trait Settings:
    def attributeKeys: Set[AttributeKey[?]]
    def keys: Iterable[ScopedKey[?]]
    def contains(key: ScopedKey[?]): Boolean
    def values: Iterable[Any]
    def data: Map[ScopedKey[?], Any]
    def scopes: Set[ScopeType]
    def getKeyValue[A](key: ScopedKey[A]): Option[(ScopedKey[A], A)]
    def get[A](key: ScopedKey[A]): Option[A]
    def definingKey[A](key: ScopedKey[A]): Option[ScopedKey[A]]
    def getDirect[A](key: ScopedKey[A]): Option[A]
    def set[A](key: ScopedKey[A], value: A): Settings
  end Settings

  private final class Settings0(
      val scopes: Set[ScopeType],
      val attributeKeys: Set[AttributeKey[?]],
      // In 1.x it was a Map[Scope, AttributeMap]
      // For the heap, it is better to store the ScopedKey[?] directly to avoid recreating
      // abd duplicating them later.
      val data: Map[ScopedKey[?], Any],
      d: ScopeType => Seq[ScopeType]
  ) extends Settings:
    def keys: Iterable[ScopedKey[?]] = data.keys
    def contains(key: ScopedKey[?]): Boolean = data.contains(key)
    def values: Iterable[Any] = data.values

    def get[A](key: ScopedKey[A]): Option[A] =
      delegates(key).flatMap(data.get).nextOption.asInstanceOf[Option[A]]

    def definingKey[A](key: ScopedKey[A]): Option[ScopedKey[A]] =
      delegates(key).find(data.contains)

    def getKeyValue[A](key: ScopedKey[A]): Option[(ScopedKey[A], A)] =
      delegates(key).flatMap { k =>
        data.get(k) match
          case None    => None
          case Some(v) => Some(k -> v.asInstanceOf[A])
      }.nextOption

    def getDirect[A](key: ScopedKey[A]): Option[A] = data.get(key).asInstanceOf[Option[A]]

    def set[A](key: ScopedKey[A], value: A): Settings =
      val newScopes = scopes + key.scope
      val newAttributeKeys = attributeKeys + key.key
      val newData = data.updated(key, value)
      Settings0(newScopes, newAttributeKeys, newData, d)

    private def delegates[A](key: ScopedKey[A]): Iterator[ScopedKey[A]] =
      d(key.scope).iterator.map(s => key.copy(scope = s))
  end Settings0

  private[sbt] abstract class ValidateKeyRef {
    def apply[T](key: ScopedKey[T], selfRefOk: Boolean): ValidatedRef[T]
  }

  /**
   * The result of this initialization is the composition of applied transformations.
   * This can be useful when dealing with dynamic Initialize values.
   */
  lazy val capturedTransformations: Initialize[[x] => Initialize[x] => Initialize[x]] =
    TransformCapture([a] => (init: Initialize[a]) => init)

  def setting[A1](
      key: ScopedKey[A1],
      init: Initialize[A1],
      pos: SourcePosition = NoPosition
  ): Setting[A1] = Setting[A1](key, init, pos)

  def valueStrict[A1](value: A1): Initialize[A1] = pure(() => value)
  def value[A1](value: => A1): Initialize[A1] = pure(() => value)
  def pure[A1](value: () => A1): Initialize[A1] = Value(value)
  def optional[A1, A2](i: Initialize[A1])(f: Option[A1] => A2): Initialize[A2] =
    Optional(Some(i), f)

  def update[A1](key: ScopedKey[A1])(f: A1 => A1): Setting[A1] =
    setting[A1](key, key(f), NoPosition)

  def flatMap[A1, A2](in: Initialize[A1])(f: A1 => Initialize[A2]): Initialize[A2] = Bind(f, in)

  private def map[A1, A2](in: Initialize[A1])(f: A1 => A2): Initialize[A2] =
    Apply[Tuple1[A1], A2](x => f(x(0)), Tuple1(in))

  def app[Tup <: Tuple, A2](inputs: Tuple.Map[Tup, Initialize])(f: Tup => A2): Initialize[A2] =
    Apply[Tup, A2](f, inputs)

  def ap[A1, A2](ff: Initialize[A1 => A2])(in: Initialize[A1]): Initialize[A2] =
    app[(A1 => A2, A1), A2]((ff, in)) { (f, a1) => f(a1) }

  def uniform[A1, A2](inputs: Seq[Initialize[A1]])(f: Seq[A1] => A2): Initialize[A2] =
    Uniform[A1, A2](f, inputs.toList)

  /**
   * The result of this initialization is the validated `key`.
   * No dependency is introduced on `key`.  If `selfRefOk` is true, validation will not fail if the key is referenced by a definition of `key`.
   * That is, key := f(validated(key).value) is allowed only if `selfRefOk == true`.
   */
  private[sbt] final def validated[A1](
      key: ScopedKey[A1],
      selfRefOk: Boolean
  ): ValidationCapture[A1] =
    ValidationCapture(key, selfRefOk)

  /**
   * Constructs a derived setting that will be automatically defined in every scope where one of its dependencies
   * is explicitly defined and the where the scope matches `filter`.
   * A setting initialized with dynamic dependencies is only allowed if `allowDynamic` is true.
   * Only the static dependencies are tracked, however.  Dependencies on previous values do not introduce a derived setting either.
   */
  final def derive[A1](
      s: Setting[A1],
      allowDynamic: Boolean = false,
      filter: ScopeType => Boolean = const(true),
      trigger: AttributeKey[?] => Boolean = const(true),
      default: Boolean = false
  ): Setting[A1] =
    deriveAllowed(s, allowDynamic) foreach sys.error
    val d = new DerivedSetting[A1](s.key, s.init, s.pos, filter, trigger)
    if default then d.default()
    else d

  def deriveAllowed[T](s: Setting[T], allowDynamic: Boolean): Option[String] = s.init match {
    case _: Bind[_, _] if !allowDynamic => Some("Cannot derive from dynamic dependencies.")
    case _                              => None
  }

  // id is used for equality
  private[sbt] final def defaultSetting[T](s: Setting[T]): Setting[T] = s.default()

  private[sbt] def defaultSettings(ss: Seq[Setting[?]]): Seq[Setting[?]] =
    ss.map(s => defaultSetting(s))

  private final val nextID = new java.util.concurrent.atomic.AtomicLong
  private final def nextDefaultID(): Long = nextID.incrementAndGet()

  def empty(using delegates: ScopeType => Seq[ScopeType]): Settings =
    Settings0(Set.empty, Set.empty, Map.empty, delegates)

  def asTransform(s: Settings): [A] => ScopedKey[A] => A =
    [A] => (sk: ScopedKey[A]) => getValue(s, sk)

  def getValue[T](s: Settings, k: ScopedKey[T]) =
    s.get(k).getOrElse(throw new InvalidReference(k))

  def asFunction[A](s: Settings): ScopedKey[A] => A = k => getValue(s, k)

  def mapScope(f: ScopeType => ScopeType): MapScoped =
    [a] => (k: ScopedKey[a]) => k.copy(scope = f(k.scope))

  private def applyDefaults(ss: Seq[Setting[?]]): Seq[Setting[?]] = {
    val result = new java.util.LinkedHashSet[Setting[?]]
    val others = new java.util.ArrayList[Setting[?]]
    ss.foreach {
      case u: DefaultSetting[_] => result.add(u)
      case r                    => others.add(r)
    }
    result.addAll(others)
    result.asScala.toVector
  }

  def compiled(init: Seq[Setting[?]], actual: Boolean = true)(using
      delegates: ScopeType => Seq[ScopeType],
      scopeLocal: ScopeLocal,
      display: Show[ScopedKey[?]]
  ): CompiledMap = {
    val initDefaults = applyDefaults(init)
    // inject derived settings into scopes where their dependencies are directly defined
    // and prepend per-scope settings
    val derived = deriveAndLocal(initDefaults, mkDelegates(delegates))
    // group by ScopeType/Key, dropping dead initializations
    val sMap: ScopedMap = grouped(derived)
    // delegate references to undefined values according to 'delegates'
    val dMap: ScopedMap =
      if (actual) delegate(sMap)(using delegates, display) else sMap
    // merge Seq[Setting[_]] into Compiled
    compile(dMap)
  }

  @deprecated("Use makeWithCompiledMap", "1.4.0")
  def make(init: Seq[Setting[?]])(using
      delegates: ScopeType => Seq[ScopeType],
      scopeLocal: ScopeLocal,
      display: Show[ScopedKey[?]]
  ): Settings = makeWithCompiledMap(init)._2

  def makeWithCompiledMap(init: Seq[Setting[?]])(using
      delegates: ScopeType => Seq[ScopeType],
      scopeLocal: ScopeLocal,
      display: Show[ScopedKey[?]]
  ): (CompiledMap, Settings) =
    val cMap = compiled(init)(using delegates, scopeLocal, display)
    // order the initializations.  cyclic references are detected here.
    val ordered: Seq[Compiled[?]] = sort(cMap)
    // evaluation: apply the initializations.
    try {
      (cMap, applyInits(ordered))
    } catch {
      case rru: RuntimeUndefined =>
        throw Uninitialized(cMap.keys.toSeq, delegates, rru.undefined, true)
    }

  def sort(cMap: CompiledMap): Seq[Compiled[?]] =
    Dag.topologicalSort(cMap.values)(_.dependencies.map(cMap))

  def compile(sMap: ScopedMap): CompiledMap =
    sMap match
      case m: IMap.IMap0[ScopedKey, SettingSeq] @unchecked =>
        import scala.collection.parallel.CollectionConverters.*
        m.backing.par
          .map { case (k, ss) =>
            val deps = ss.iterator.flatMap(_.dependencies).toSet
            k -> Compiled(k.asInstanceOf[ScopedKey[Any]], deps, ss.asInstanceOf[SettingSeq[Any]])
          }
          .to(Map)

      case _ =>
        sMap.toTypedSeq.map { case sMap.TPair(k, ss) =>
          val deps = ss.flatMap(_.dependencies)
          (k, Compiled(k, deps, ss))
        }.toMap

  def grouped(init: Seq[Setting[?]]): ScopedMap =
    val result = new java.util.HashMap[ScopedKey[?], Seq[Setting[?]]]
    init.foreach { s =>
      result.putIfAbsent(s.key, Vector(s)) match {
        case null =>
        case ss   => result.put(s.key, if (s.definitive) Vector(s) else ss :+ s)
      }
    }
    IMap.fromJMap[ScopedKey, SettingSeq](
      result.asInstanceOf[java.util.Map[ScopedKey[Any], SettingSeq[Any]]]
    )

  def add[A1](m: ScopedMap, s: Setting[A1]): ScopedMap =
    m.mapValue[A1](s.key, Vector.empty[Setting[A1]], ss => append(ss, s))

  def append[A1](ss: Seq[Setting[A1]], s: Setting[A1]): Seq[Setting[A1]] =
    if s.definitive then Vector(s)
    else ss :+ s

  def addLocal(init: Seq[Setting[?]])(using scopeLocal: ScopeLocal): Seq[Setting[?]] =
    Par(init).map(_.dependencies flatMap scopeLocal).toVector.flatten ++ init

  def delegate(sMap: ScopedMap)(using
      delegates: ScopeType => Seq[ScopeType],
      display: Show[ScopedKey[?]]
  ): ScopedMap = {
    def refMap(ref: Setting[?], isFirst: Boolean) = new ValidateKeyRef {
      def apply[T](k: ScopedKey[T], selfRefOk: Boolean) =
        delegateForKey(sMap, k, delegates(k.scope), ref, selfRefOk || !isFirst)
    }

    val undefined = new java.util.ArrayList[Undefined]
    val result = new java.util.concurrent.ConcurrentHashMap[ScopedKey[?], Any]
    val backing = sMap.toSeq
    Par(backing).foreach { case (key, settings) =>
      val valid = new java.util.ArrayList[Setting[?]]
      val undefs = new java.util.ArrayList[Undefined]
      def validate(s: Setting[?], first: Boolean): Unit = {
        s.validateKeyReferenced(refMap(s, first)) match {
          case Right(v) => valid.add(v); ()
          case Left(us) => us.foreach(u => undefs.add(u))
        }
      }
      settings.headOption match {
        case Some(s) =>
          validate(s, true)
          settings.tail.foreach(validate(_, false))
        case _ =>
      }
      if (undefs.isEmpty) result.put(key, valid.asScala.toVector)
      else undefined.addAll(undefs)
    }

    if undefined.isEmpty then
      IMap.fromJMap[ScopedKey, SettingSeq](
        result.asInstanceOf[java.util.Map[ScopedKey[Any], SettingSeq[Any]]]
      )
    else throw Uninitialized(sMap.keys.toSeq, delegates, undefined.asScala.toList, false)
  }

  private def delegateForKey[A1](
      sMap: ScopedMap,
      k: ScopedKey[A1],
      scopes: Seq[ScopeType],
      ref: Setting[?],
      selfRefOk: Boolean
  ): Either[Undefined, ScopedKey[A1]] =
    val skeys = scopes.iterator.map(x => ScopedKey(x, k.key))
    val definedAt = skeys.find(sk => (selfRefOk || ref.key != sk) && (sMap.contains(sk)))
    definedAt.toRight(Undefined(ref, k))

  private def applyInits(ordered: Seq[Compiled[?]])(using
      delegates: ScopeType => Seq[ScopeType]
  ): Settings =
    val x =
      java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)
    try {
      val eval: EvaluateSettings[Init.this.type] = new EvaluateSettings(Init.this, x, ordered)
      eval.run(using delegates)
    } finally {
      x.shutdown()
    }

  def showUndefined(
      u: Undefined,
      validKeys: Seq[ScopedKey[?]],
      delegates: ScopeType => Seq[ScopeType]
  )(using
      display: Show[ScopedKey[?]]
  ): String =
    val guessed = guessIntendedScope(validKeys, delegates, u.referencedKey)
    val derived = u.defining.isDerived
    val refString = display.show(u.defining.key)
    val sourceString = if derived then "" else parenPosString(u.defining)
    val guessedString =
      if derived then ""
      else guessed.map(g => "\n     Did you mean " + display.show(g) + " ?").toList.mkString
    val derivedString =
      if derived then
        ", which is a derived setting that needs this key to be defined in this scope."
      else ""
    display.show(
      u.referencedKey
    ) + " from " + refString + sourceString + derivedString + guessedString

  private def parenPosString(s: Setting[?]): String =
    s.positionString match { case None => ""; case Some(s) => " (" + s + ")" }

  def guessIntendedScope(
      validKeys: Seq[ScopedKey[?]],
      delegates: ScopeType => Seq[ScopeType],
      key: ScopedKey[?]
  ): Option[ScopedKey[?]] =
    val distances = validKeys.flatMap { validKey =>
      refinedDistance(delegates, validKey, key).map(dist => (dist, validKey))
    }
    distances.sortBy(_._1).map(_._2).headOption

  def refinedDistance(
      delegates: ScopeType => Seq[ScopeType],
      a: ScopedKey[?],
      b: ScopedKey[?]
  ): Option[Int] =
    if a.key != b.key || a == b then None
    else {
      val dist = delegates(a.scope).indexOf(b.scope)
      if dist < 0 then None
      else Some(dist)
    }

  final class Uninitialized(val undefined: Seq[Undefined], override val toString: String)
      extends Exception(toString)

  def Uninitialized(
      validKeys: Seq[ScopedKey[?]],
      delegates: ScopeType => Seq[ScopeType],
      keys: Seq[Undefined],
      runtime: Boolean
  )(using display: Show[ScopedKey[?]]): Uninitialized = {
    assert(keys.nonEmpty)
    val suffix = if (keys.length > 1) "s" else ""
    val prefix = if (runtime) "Runtime reference" else "Reference"
    val keysString =
      keys.map(u => showUndefined(u, validKeys, delegates)).mkString("\n\n  ", "\n\n  ", "")
    new Uninitialized(
      keys,
      prefix + suffix + " to undefined setting" + suffix + ": " + keysString + "\n "
    )
  }

  final class Compiled[A1](
      val key: ScopedKey[A1],
      val dependencies: Iterable[ScopedKey[?]],
      val settings: Seq[Setting[A1]]
  ):
    override def toString = showFullKey.show(key)
  end Compiled

  final class Flattened(val key: ScopedKey[?], val dependencies: Iterable[ScopedKey[?]])

  def flattenLocals(compiled: CompiledMap): Map[ScopedKey[?], Flattened] = {
    val locals = compiled.collect { case (key, comp) if key.key.isLocal => comp }
    val ordered = Dag.topologicalSort(locals)(
      _.dependencies.collect { case dep if dep.key.isLocal => compiled(dep) }
    )
    def flatten(
        cmap: Map[ScopedKey[?], Flattened],
        key: ScopedKey[?],
        deps: Iterable[ScopedKey[?]]
    ): Flattened =
      new Flattened(
        key,
        deps.flatMap(dep => if (dep.key.isLocal) cmap(dep).dependencies else Seq(dep))
      )

    val empty = Map.empty[ScopedKey[?], Flattened]

    val flattenedLocals = ordered.foldLeft(empty) { (cmap, c) =>
      cmap.updated(c.key, flatten(cmap, c.key, c.dependencies))
    }

    compiled.collect {
      case (key, comp) if !key.key.isLocal =>
        (key, flatten(flattenedLocals, key, comp.dependencies))
    }
  }

  def definedAtString(settings: Seq[Setting[?]]): String = {
    val posDefined = settings.flatMap(_.positionString.toList)
    if (posDefined.nonEmpty) {
      val header =
        if (posDefined.size == settings.size) "defined at:"
        else
          "some of the defining occurrences:"
      header + (posDefined.distinct.mkString("\n\t", "\n\t", "\n"))
    } else ""
  }

  /**
   *  The intersect method was calling Seq.contains which is very slow compared
   *  to converting the Seq to a Set and calling contains on the Set. This
   *  private trait abstracts out the two ways that Seq[ScopeType] was actually
   *  used, `contains` and `exists`. In mkDelegates, we can create and cache
   *  instances of Delegates so that we don't have to repeatedly convert the
   *  same Seq to Set. On a 2020 16" macbook pro, creating the compiled map
   *  for the sbt project is roughly 2 seconds faster after this change
   *  (about 3.5 seconds before compared to about 1.5 seconds after)
   */
  private trait Delegates:
    def contains(s: ScopeType): Boolean
    def exists(f: ScopeType => Boolean): Boolean
  end Delegates

  private def mkDelegates(delegates: ScopeType => Seq[ScopeType]): ScopeType => Delegates = {
    val delegateMap = new java.util.concurrent.ConcurrentHashMap[ScopeType, Delegates]
    s =>
      delegateMap.get(s) match {
        case null =>
          val seq = delegates(s)
          val set = seq.toSet
          val d = new Delegates {
            override def contains(s: ScopeType): Boolean = set.contains(s)
            override def exists(f: ScopeType => Boolean): Boolean = seq.exists(f)
          }
          delegateMap.put(s, d)
          d
        case d => d
      }
  }

  /**
   * Intersects two scopes, returning the more specific one if they intersect, or None otherwise.
   */
  private[sbt] def intersect(s1: ScopeType, s2: ScopeType)(using
      delegates: ScopeType => Seq[ScopeType]
  ): Option[ScopeType] = intersectDelegates(s1, s2, mkDelegates(delegates))

  /**
   * Intersects two scopes, returning the more specific one if they intersect, or None otherwise.
   */
  private def intersectDelegates(
      s1: ScopeType,
      s2: ScopeType,
      delegates: ScopeType => Delegates
  ): Option[ScopeType] =
    if (delegates(s1).contains(s2)) Some(s1) // s1 is more specific
    else if (delegates(s2).contains(s1)) Some(s2) // s2 is more specific
    else None

  private def deriveAndLocal(init: Seq[Setting[?]], delegates: ScopeType => Delegates)(using
      scopeLocal: ScopeLocal
  ): Seq[Setting[?]] = {
    import collection.mutable

    final class Derived(val setting: DerivedSetting[?]) {
      val dependencies = setting.dependencies.map(_.key)
      def triggeredBy = dependencies.filter(setting.trigger)
      val inScopes = new mutable.HashSet[ScopeType]
      val outputs = new mutable.ListBuffer[Setting[?]]
    }

    final class Deriveds(val key: AttributeKey[?], val settings: mutable.ListBuffer[Derived]) {
      def dependencies = settings.flatMap(_.dependencies)
      // This is mainly for use in the cyclic reference error message
      override def toString =
        s"Derived settings for ${key.label}, ${definedAtString(settings.map(_.setting).toSeq)}"
    }

    // separate `derived` settings from normal settings (`defs`)
    val (derived, rawDefs) =
      Util.separate[Setting[?], Derived, Setting[?]](init) {
        case d: DerivedSetting[_] => Left(new Derived(d)); case s => Right(s)
      }
    val defs = addLocal(rawDefs)(using scopeLocal)

    // group derived settings by the key they define
    val derivsByDef = new mutable.HashMap[AttributeKey[?], Deriveds]
    for (s <- derived) {
      val key = s.setting.key.key
      derivsByDef.getOrElseUpdate(key, new Deriveds(key, new mutable.ListBuffer)).settings += s
    }

    // index derived settings by triggering key.  This maps a key to the list of settings potentially derived from it.
    val derivedBy = new mutable.HashMap[AttributeKey[?], mutable.ListBuffer[Derived]]
    for (s <- derived; d <- s.triggeredBy)
      derivedBy.getOrElseUpdate(d, new mutable.ListBuffer) += s

    // Map a DerivedSetting[_] to the `Derived` struct wrapping it. Used to ultimately replace a DerivedSetting with
    // the `Setting`s that were actually derived from it: `Derived.outputs`
    val derivedToStruct: Map[DerivedSetting[?], Derived] = (derived map { s =>
      s.setting -> s
    }).toMap

    // set of defined scoped keys, used to ensure a derived setting is only added if all dependencies are present
    val defined = new mutable.HashSet[ScopedKey[?]]
    def addDefs(ss: Seq[Setting[?]]): Unit = { for (s <- ss) defined += s.key }
    addDefs(defs)

    // true iff the scoped key is in `defined`, taking delegation into account
    def isDefined(key: AttributeKey[?], scope: ScopeType) =
      delegates(scope).exists(s => defined.contains(ScopedKey(s, key)))

    // true iff all dependencies of derived setting `d` have a value (potentially via delegation) in `scope`
    def allDepsDefined(d: Derived, scope: ScopeType, local: Set[AttributeKey[?]]): Boolean =
      d.dependencies.forall(dep => local(dep) || isDefined(dep, scope))

    // Returns the list of injectable derived settings and their local settings for `sk`.
    // The settings are to be injected under `outputScope` = whichever scope is more specific of:
    //   * the dependency's (`sk`) scope
    //   * the DerivedSetting's scope in which it has been declared, `definingScope`
    // provided that these two scopes intersect.
    //  A derived setting is injectable if:
    //   1. it has not been previously injected into outputScope
    //   2. it applies to outputScope (as determined by its `filter`)
    //   3. all of its dependencies are defined for outputScope (allowing for delegation)
    // This needs to handle local settings because a derived setting wouldn't be injected if it's local setting didn't exist yet.
    val deriveFor = (sk: ScopedKey[?]) => {
      val derivedForKey: List[Derived] = derivedBy.get(sk.key).toList.flatten
      val scope = sk.scope
      def localAndDerived(d: Derived): Seq[Setting[?]] = {
        def definingScope = d.setting.key.scope
        val outputScope = intersectDelegates(scope, definingScope, delegates)
        outputScope collect {
          case s if !d.inScopes.contains(s) && d.setting.filter(s) =>
            val local = d.dependencies.flatMap(dep => scopeLocal(ScopedKey(s, dep)))
            if (allDepsDefined(d, s, local.map(_.key.key).toSet)) {
              d.inScopes.add(s)
              val out = local :+ d.setting.setScope(s)
              d.outputs ++= out
              out
            } else nilSeq
        } getOrElse nilSeq
      }
      derivedForKey.flatMap(localAndDerived)
    }

    val processed = new mutable.HashSet[ScopedKey[?]]

    // derives settings, transitively so that a derived setting can trigger another
    def process(rem: List[Setting[?]]): Unit = rem match {
      case s :: ss =>
        val sk = s.key
        val ds = if (processed.add(sk)) deriveFor(sk) else nil
        addDefs(ds)
        process(ds ::: ss)
      case Nil =>
    }
    process(defs.toList)

    // Take all the original defs and DerivedSettings along with locals, replace each DerivedSetting with the actual
    // settings that were derived.
    val allDefs = addLocal(init)(using scopeLocal)
    allDefs.flatMap {
      case d: DerivedSetting[_] => (derivedToStruct get d map (_.outputs)).toSeq.flatten
      case s                    => s :: nil
    }
  }

  extension (f: [x] => Initialize[x] => Initialize[x])
    def ∙(g: [x] => Initialize[x] => Initialize[x]): [x] => Initialize[x] => Initialize[x] =
      [x] => (f3: Initialize[x]) => f(g(f3))

  extension (f: [x] => ValidatedInit[x] => Initialize[x])
    def composeVI(
        g: [x] => Initialize[x] => ValidatedInit[x]
    ): [x] => Initialize[x] => Initialize[x] =
      [x] => (f3: Initialize[x]) => f(g(f3))

  /**
   * Abstractly defines a value of type `A1`.
   *
   * Specifically it defines a node in a task graph,
   * where the `dependencies` represents dependent nodes,
   * and `evaluate` represents the calculation based on the existing body of knowledge.
   *
   * @tparam A1 the type of the value this defines.
   */
  sealed trait Initialize[A1]:
    def dependencies: Seq[ScopedKey[?]]
    def apply[A2](g: A1 => A2): Initialize[A2]

    private[sbt] def mapReferenced(g: MapScoped): Initialize[A1]
    private[sbt] def mapConstant(g: MapConstant): Initialize[A1]

    private[sbt] def validateReferenced(g: ValidateRef): ValidatedInit[A1] =
      validateKeyReferenced(new ValidateKeyRef {
        def apply[A2](key: ScopedKey[A2], selfRefOk: Boolean) = g(key)
      })

    private[sbt] def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[A1]

    def evaluate(map: Settings): A1
    def zip[A2](o: Initialize[A2]): Initialize[(A1, A2)] = zipTupled(o)(identity)

    def zipWith[A2, U](o: Initialize[A2])(f: (A1, A2) => U): Initialize[U] =
      zipTupled(o)(f.tupled)

    private def zipTupled[A2, U](o: Initialize[A2])(f: ((A1, A2)) => U): Initialize[U] =
      Apply[(A1, A2), U](f, (this, o))

    /** A fold on the static attributes of this and nested Initializes. */
    private[sbt] def processAttributes[S](init: S)(f: (S, AttributeMap) => S): S
  end Initialize

  object Initialize:
    implicit def joinInitialize[A1](s: Seq[Initialize[A1]]): JoinInitSeq[A1] = new JoinInitSeq(s)

    final class JoinInitSeq[A1](s: Seq[Initialize[A1]]):
      def joinWith[A2](f: Seq[A1] => A2): Initialize[A2] = uniform(s)(f)
      def join: Initialize[Seq[A1]] = uniform(s)(identity)
    end JoinInitSeq

    def join[A1](inits: Seq[Initialize[A1]]): Initialize[Seq[A1]] = uniform(inits)(identity)

    def joinAny[F[_]]: [a] => Seq[Initialize[F[a]]] => Initialize[Seq[F[Any]]] = [a] =>
      (inits: Seq[Initialize[F[a]]]) => join(inits.asInstanceOf[Seq[Initialize[F[Any]]]])
  end Initialize

  object SettingsDefinition:
    implicit def unwrapSettingsDefinition(d: SettingsDefinition): Seq[Setting[?]] = d.settings
    implicit def wrapSettingsDefinition(ss: Seq[Setting[?]]): SettingsDefinition =
      new SettingList(ss)
  end SettingsDefinition

  sealed trait SettingsDefinition:
    def settings: Seq[Setting[?]]
  end SettingsDefinition

  final class SettingList(override val settings: Seq[Setting[?]]) extends SettingsDefinition

  sealed class Setting[A1] private[Init] (
      val key: ScopedKey[A1],
      val init: Initialize[A1],
      val pos: SourcePosition
  ) extends SettingsDefinition:
    override def settings = this :: Nil
    def definitive: Boolean = !init.dependencies.contains(key)
    def dependencies: Seq[ScopedKey[?]] =
      remove(init.dependencies.asInstanceOf[Seq[ScopedKey[A1]]], key)

    def mapReferenced(g: MapScoped): Setting[A1] = make(key, init.mapReferenced(g), pos)

    def validateReferenced(g: ValidateRef): Either[Seq[Undefined], Setting[A1]] =
      init.validateReferenced(g).map(newI => make(key, newI, pos))

    private[sbt] def validateKeyReferenced(g: ValidateKeyRef): Either[Seq[Undefined], Setting[A1]] =
      init.validateKeyReferenced(g).map(newI => make(key, newI, pos))

    def mapKey(g: MapScoped): Setting[A1] = make(g(key), init, pos)
    def mapInit(f: (ScopedKey[A1], A1) => A1): Setting[A1] = make(key, init(t => f(key, t)), pos)
    def mapConstant(g: MapConstant): Setting[A1] = make(key, init.mapConstant(g), pos)
    def withPos(pos: SourcePosition) = make(key, init, pos)

    def positionString: Option[String] = pos match
      case pos: FilePosition => Some(pos.path + ":" + pos.startLine)
      case NoPosition        => None

    private[sbt] def mapInitialize(f: Initialize[A1] => Initialize[A1]): Setting[A1] =
      make(key, f(init), pos)

    override def toString = "setting(" + key + ") at " + pos

    protected def make[A2](
        key: ScopedKey[A2],
        init: Initialize[A2],
        pos: SourcePosition
    ): Setting[A2] = Setting[A2](key, init, pos)

    protected[sbt] def isDerived: Boolean = false
    private[sbt] def setScope(s: ScopeType): Setting[A1] =
      make(key.copy(scope = s), init.mapReferenced(mapScope(const(s))), pos)

    /** Turn this setting into a `DefaultSetting` if it's not already, otherwise returns `this` */
    private[sbt] def default(id: => Long = nextDefaultID()): DefaultSetting[A1] =
      DefaultSetting(key, init, pos, id)
  end Setting

  private[Init] sealed class DerivedSetting[A1](
      sk: ScopedKey[A1],
      i: Initialize[A1],
      p: SourcePosition,
      val filter: ScopeType => Boolean,
      val trigger: AttributeKey[?] => Boolean
  ) extends Setting[A1](sk, i, p):

    override def make[B](key: ScopedKey[B], init: Initialize[B], pos: SourcePosition): Setting[B] =
      new DerivedSetting[B](key, init, pos, filter, trigger)

    protected[sbt] override def isDerived: Boolean = true

    override def default(_id: => Long): DefaultSetting[A1] =
      new DerivedSetting[A1](sk, i, p, filter, trigger) with DefaultSetting[A1] { val id = _id }

    override def toString = "derived " + super.toString
  end DerivedSetting

  // Only keep the first occurrence of this setting and move it to the front so that it has lower precedence than non-defaults.
  //  This is intended for internal sbt use only, where alternatives like Plugin.globalSettings are not available.
  private[Init] sealed trait DefaultSetting[A1] extends Setting[A1]:
    val id: Long

    override def make[B](key: ScopedKey[B], init: Initialize[B], pos: SourcePosition): Setting[B] =
      super.make(key, init, pos).default(id)

    override final def hashCode = id.hashCode

    override final def equals(o: Any): Boolean =
      o match
        case d: DefaultSetting[_] => d.id == id
        case _                    => false

    override def toString: String = s"default($id) " + super.toString
    override def default(id: => Long) = this
  end DefaultSetting

  object DefaultSetting:
    def apply[A1](sk: ScopedKey[A1], i: Initialize[A1], p: SourcePosition, _id: Long) =
      new Setting[A1](sk, i, p) with DefaultSetting[A1]:
        val id = _id
  end DefaultSetting

  private def handleUndefined[A](vr: ValidatedInit[A]): Initialize[A] = vr match
    case Left(undefs) => throw new RuntimeUndefined(undefs)
    case Right(x)     => x

  private lazy val getValidatedK = [A] => (fa: ValidatedInit[A]) => handleUndefined(fa)

  // mainly for reducing generated class count
  private def validateKeyReferencedK(
      g: ValidateKeyRef
  ): [A] => Initialize[A] => ValidatedInit[A] = [A] =>
    (fa: Initialize[A]) => (fa.validateKeyReferenced(g))

  private def mapReferencedK(g: MapScoped): [A] => Initialize[A] => Initialize[A] = [A] =>
    (fa: Initialize[A]) => (fa.mapReferenced(g))
  private def mapConstantK(g: MapConstant): [A] => Initialize[A] => Initialize[A] = [A] =>
    (fa: Initialize[A]) => (fa.mapConstant(g))
  private def evaluateK(g: Settings): [A] => Initialize[A] => A = [A] =>
    (fa: Initialize[A]) => (fa.evaluate(g))
  private def deps(ls: List[Initialize[?]]): Seq[ScopedKey[?]] =
    ls.flatMap(_.dependencies)

  /**
   * An `Initialize[T]` associated with a `ScopedKey[S]`.
   * @tparam S the type of the associated `ScopedKey`
   * @tparam T the type of the value this `Initialize` defines.
   */
  sealed trait Keyed[S, A1] extends Initialize[A1]:
    def scopedKey: ScopedKey[S]
    override final def dependencies = scopedKey :: Nil
    private[sbt] override def processAttributes[A2](init: A2)(f: (A2, AttributeMap) => A2): A2 =
      init
  end Keyed

  final class GetValue[S, A1](val scopedKey: ScopedKey[S], val transform: S => A1)
      extends Keyed[S, A1]:
    override final def apply[A2](g: A1 => A2): Initialize[A2] =
      GetValue(scopedKey, g compose transform)
    override final def evaluate(ss: Settings): A1 = transform(getValue(ss, scopedKey))
    override final def mapReferenced(g: MapScoped): Initialize[A1] =
      GetValue(g(scopedKey), transform)

    private[sbt] override final def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[A1] =
      g(scopedKey, false) match
        case Left(un)  => Left(un :: Nil)
        case Right(nk) => Right(GetValue(nk, transform))

    override final def mapConstant(g: MapConstant): Initialize[A1] =
      g(scopedKey) match
        case None        => this
        case Some(const) => Value(() => transform(const))
  end GetValue

  /**
   * A `Keyed` where the type of the value and the associated `ScopedKey` are the same.
   * @tparam A1 the type of both the value this `Initialize` defines and the type of the associated `ScopedKey`.
   */
  trait KeyedInitialize[A1] extends Keyed[A1, A1]:
    override final def apply[A2](g: A1 => A2): Initialize[A2] =
      GetValue(scopedKey, g)
    override final def evaluate(ss: Settings): A1 = getValue(ss, scopedKey)
    override final def mapReferenced(g: MapScoped): Initialize[A1] = g(scopedKey)

    private[sbt] override final def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[A1] =
      g(scopedKey, false) match
        case Left(un)  => Left(un :: Nil)
        case Right(nk) => Right(nk)

    override final def mapConstant(g: MapConstant): Initialize[A1] =
      g(scopedKey) match
        case None        => this
        case Some(const) => Value(() => const)
  end KeyedInitialize

  private[sbt] final class TransformCapture(val f: [x] => Initialize[x] => Initialize[x])
      extends Initialize[[x] => Initialize[x] => Initialize[x]]:
    override def dependencies: Seq[ScopedKey[?]] = Nil
    override def apply[A2](g2: ([x] => Initialize[x] => Initialize[x]) => A2): Initialize[A2] =
      map(this)(g2)
    override def evaluate(ss: Settings): [x] => Initialize[x] => Initialize[x] = f
    override def mapReferenced(g: MapScoped): Initialize[[x] => Initialize[x] => Initialize[x]] =
      TransformCapture(mapReferencedK(g) ∙ f)
    override def mapConstant(g: MapConstant): Initialize[[x] => Initialize[x] => Initialize[x]] =
      TransformCapture(mapConstantK(g) ∙ f)

    override def validateKeyReferenced(
        g: ValidateKeyRef
    ): ValidatedInit[[x] => Initialize[x] => Initialize[x]] =
      Right(TransformCapture(getValidatedK.composeVI(validateKeyReferencedK(g)) ∙ f))

    private[sbt] override def processAttributes[A2](init: A2)(f: (A2, AttributeMap) => A2): A2 =
      init
  end TransformCapture

  private[sbt] final class ValidationCapture[A1](val key: ScopedKey[A1], val selfRefOk: Boolean)
      extends Initialize[ScopedKey[A1]]:
    override def dependencies: Seq[ScopedKey[?]] = Nil
    override def apply[A2](g2: ScopedKey[A1] => A2): Initialize[A2] = map(this)(g2)
    override def evaluate(ss: Settings): ScopedKey[A1] = key
    override def mapReferenced(g: MapScoped): Initialize[ScopedKey[A1]] =
      ValidationCapture(g(key), selfRefOk)
    override def mapConstant(g: MapConstant): Initialize[ScopedKey[A1]] = this

    override def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[ScopedKey[A1]] =
      g(key, selfRefOk) match
        case Left(un) => Left(un :: Nil)
        case Right(k) => Right(ValidationCapture(k, selfRefOk))

    private[sbt] override def processAttributes[A2](init: A2)(f: (A2, AttributeMap) => A2): A2 =
      init
  end ValidationCapture

  private[sbt] final class Bind[S, A1](val f: S => Initialize[A1], val in: Initialize[S])
      extends Initialize[A1]:
    override def dependencies: Seq[ScopedKey[?]] = in.dependencies
    override def apply[A2](g: A1 => A2): Initialize[A2] = Bind[S, A2](s => f(s)(g), in)
    override def evaluate(ss: Settings): A1 = f(in.evaluate(ss)).evaluate(ss)
    override def mapReferenced(g: MapScoped) =
      Bind[S, A1](s => f(s).mapReferenced(g), in.mapReferenced(g))

    override def validateKeyReferenced(g: ValidateKeyRef) =
      in.validateKeyReferenced(g).map { validIn =>
        Bind[S, A1](s => handleUndefined(f(s).validateKeyReferenced(g)), validIn)
      }

    override def mapConstant(g: MapConstant) =
      Bind[S, A1](s => f(s).mapConstant(g), in.mapConstant(g))

    private[sbt] override def processAttributes[A2](init: A2)(f: (A2, AttributeMap) => A2): A2 =
      in.processAttributes(init)(f)
  end Bind

  private[sbt] final class Optional[S, A1](val a: Option[Initialize[S]], val f: Option[S] => A1)
      extends Initialize[A1]:
    override def dependencies: Seq[ScopedKey[?]] = deps(a.toList)
    override def apply[A2](g: A1 => A2): Initialize[A2] = new Optional[S, A2](a, g compose f)

    override def mapReferenced(g: MapScoped): Initialize[A1] =
      Optional(a.map { mapReferencedK(g)[S] }, f)

    override def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[A1] = a match
      case None    => Right(this)
      case Some(i) => Right(Optional(i.validateKeyReferenced(g).toOption, f))

    override def mapConstant(g: MapConstant): Initialize[A1] = Optional(a map mapConstantK(g)[S], f)
    override def evaluate(ss: Settings): A1 =
      f(a.flatMap { i => trapBadRef(evaluateK(ss)(i)) })

    // proper solution is for evaluate to be deprecated or for external use only and a new internal method returning Either be used
    private def trapBadRef[A](run: => A): Option[A] =
      try Some(run)
      catch { case _: InvalidReference => None }

    private[sbt] override def processAttributes[B](init: B)(f: (B, AttributeMap) => B): B = a match
      case None    => init
      case Some(i) => i.processAttributes(init)(f)
  end Optional

  private[sbt] final class Value[A1](val value: () => A1) extends Initialize[A1]:
    override def dependencies: Seq[ScopedKey[?]] = Nil
    override def mapReferenced(g: MapScoped): Initialize[A1] = this
    override def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[A1] = Right(this)
    override def apply[A2](g: A1 => A2): Initialize[A2] = Value[A2](() => g(value()))
    override def mapConstant(g: MapConstant): Initialize[A1] = this
    override def evaluate(map: Settings): A1 = value()
    private[sbt] override def processAttributes[A2](init: A2)(f: (A2, AttributeMap) => A2): A2 =
      init
  end Value

  private[sbt] object StaticScopes extends Initialize[Set[ScopeType]]:
    override def dependencies: Seq[ScopedKey[?]] = Nil
    override def mapReferenced(g: MapScoped): Initialize[Set[ScopeType]] = this
    override def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[Set[ScopeType]] =
      Right(this)
    override def apply[A2](g: Set[ScopeType] => A2) = map(this)(g)
    override def mapConstant(g: MapConstant): Initialize[Set[ScopeType]] = this
    override def evaluate(map: Settings): Set[ScopeType] = map.scopes
    private[sbt] override def processAttributes[A2](init: A2)(f: (A2, AttributeMap) => A2): A2 =
      init
  end StaticScopes

  private[sbt] final class Uniform[A1, A2](val f: Seq[A1] => A2, val inputs: List[Initialize[A1]])
      extends Initialize[A2]:
    override def dependencies: Seq[ScopedKey[?]] = deps(inputs)
    override def mapReferenced(g: MapScoped): Initialize[A2] =
      Uniform(f, inputs.map(_.mapReferenced(g)))
    override def mapConstant(g: MapConstant): Initialize[A2] =
      Uniform(f, inputs.map(_.mapConstant(g)))
    override def apply[A3](g: A2 => A3): Initialize[A3] = Uniform(g.compose(f), inputs)
    override def evaluate(ss: Settings): A2 = f(inputs.map(_.evaluate(ss)))

    override def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[A2] =
      val tx = inputs.map(_.validateKeyReferenced(g))
      val undefs = tx.flatMap(_.left.toSeq.flatten)
      if undefs.isEmpty then Right(Uniform(f, tx.map(_.toOption.get)))
      else Left(undefs)

    private[sbt] override def processAttributes[A2](init: A2)(f: (A2, AttributeMap) => A2): A2 =
      inputs.foldLeft(init)((v, i) => i.processAttributes(v)(f))
  end Uniform

  private[sbt] final class Apply[Tup <: Tuple, A1](
      val f: Tup => A1,
      val inputs: Tuple.Map[Tup, Initialize]
  ) extends Initialize[A1]:
    import sbt.internal.util.TupleMapExtension.*

    override def dependencies: Seq[ScopedKey[?]] = deps(inputs.toList0)
    override def mapReferenced(g: MapScoped): Initialize[A1] =
      Apply(f, inputs.transform(mapReferencedK(g)))
    override def mapConstant(g: MapConstant): Initialize[A1] =
      Apply(f, inputs.transform(mapConstantK(g)))

    override def apply[A2](g: A1 => A2): Initialize[A2] = Apply(g compose f, inputs)

    override def evaluate(ss: Settings): A1 = f(inputs.unmap(evaluateK(ss)))

    override def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[A1] =
      val tx: Tuple.Map[Tup, ValidatedInit] = inputs.transform(validateKeyReferencedK(g))
      val undefs = tx.iterator.flatMap(_.left.getOrElse(Seq.empty))
      val get = [A] => (fa: ValidatedInit[A]) => fa.toOption.get
      if undefs.isEmpty then Right(Apply(f, tx.transform(get)))
      else Left(undefs.toSeq)

    private[sbt] override def processAttributes[A2](init: A2)(f: (A2, AttributeMap) => A2): A2 =
      inputs.toList0.foldLeft(init) { (v, i) => i.processAttributes(v)(f) }
  end Apply

  private def remove[A](s: Seq[A], v: A) = s.filterNot(_ == v)

  final class Undefined private[sbt] (val defining: Setting[?], val referencedKey: ScopedKey[?])

  def Undefined(defining: Setting[?], referencedKey: ScopedKey[?]): Undefined =
    new Undefined(defining, referencedKey)

  final class RuntimeUndefined(val undefined: Seq[Undefined])
      extends RuntimeException("References to undefined settings at runtime."):
    override def getMessage =
      super.getMessage + undefined.map { u =>
        "\n" + u.referencedKey + " referenced from " + u.defining
      }.mkString
  end RuntimeUndefined

  private final class InvalidReference(val key: ScopedKey[?])
      extends RuntimeException(
        "Internal settings error: invalid reference to " + showFullKey.show(key)
      )
end Init
