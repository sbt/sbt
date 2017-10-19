/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import scala.language.existentials

import Types._
import sbt.util.Show

sealed trait Settings[Scope] {
  def data: Map[Scope, AttributeMap]
  def keys(scope: Scope): Set[AttributeKey[_]]
  def scopes: Set[Scope]
  def definingScope(scope: Scope, key: AttributeKey[_]): Option[Scope]
  def allKeys[T](f: (Scope, AttributeKey[_]) => T): Seq[T]
  def get[T](scope: Scope, key: AttributeKey[T]): Option[T]
  def getDirect[T](scope: Scope, key: AttributeKey[T]): Option[T]
  def set[T](scope: Scope, key: AttributeKey[T], value: T): Settings[Scope]
}

private final class Settings0[Scope](
    val data: Map[Scope, AttributeMap],
    val delegates: Scope => Seq[Scope]
) extends Settings[Scope] {

  def scopes: Set[Scope] = data.keySet
  def keys(scope: Scope) = data(scope).keys.toSet

  def allKeys[T](f: (Scope, AttributeKey[_]) => T): Seq[T] =
    data.flatMap { case (scope, map) => map.keys.map(k => f(scope, k)) }.toSeq

  def get[T](scope: Scope, key: AttributeKey[T]): Option[T] =
    delegates(scope).toStream.flatMap(sc => getDirect(sc, key)).headOption

  def definingScope(scope: Scope, key: AttributeKey[_]): Option[Scope] =
    delegates(scope).toStream.find(sc => getDirect(sc, key).isDefined)

  def getDirect[T](scope: Scope, key: AttributeKey[T]): Option[T] =
    (data get scope).flatMap(_ get key)

  def set[T](scope: Scope, key: AttributeKey[T], value: T): Settings[Scope] = {
    val map = data getOrElse (scope, AttributeMap.empty)
    val newData = data.updated(scope, map.put(key, value))
    new Settings0(newData, delegates)
  }
}

// delegates should contain the input Scope as the first entry
// this trait is intended to be mixed into an object
trait Init[Scope] {

  /** The Show instance used when a detailed String needs to be generated.
   *  It is typically used when no context is available.
   */
  def showFullKey: Show[ScopedKey[_]]

  sealed case class ScopedKey[T](scope: Scope, key: AttributeKey[T]) extends KeyedInitialize[T] {
    def scopedKey = this
  }

  type SettingSeq[T] = Seq[Setting[T]]
  type ScopedMap = IMap[ScopedKey, SettingSeq]
  type CompiledMap = Map[ScopedKey[_], Compiled[_]]
  type MapScoped = ScopedKey ~> ScopedKey
  type ValidatedRef[T] = Either[Undefined, ScopedKey[T]]
  type ValidatedInit[T] = Either[Seq[Undefined], Initialize[T]]
  type ValidateRef = ScopedKey ~> ValidatedRef
  type ScopeLocal = ScopedKey[_] => Seq[Setting[_]]
  type MapConstant = ScopedKey ~> Option

  private[sbt] abstract class ValidateKeyRef {
    def apply[T](key: ScopedKey[T], selfRefOk: Boolean): ValidatedRef[T]
  }

  /**
   * The result of this initialization is the composition of applied transformations.
   * This can be useful when dealing with dynamic Initialize values.
   */
  lazy val capturedTransformations: Initialize[Initialize ~> Initialize] =
    new TransformCapture(idK[Initialize])

  def setting[T](
      key: ScopedKey[T],
      init: Initialize[T],
      pos: SourcePosition = NoPosition
  ): Setting[T] = new Setting[T](key, init, pos)

  def valueStrict[T](value: T): Initialize[T] = pure(() => value)
  def value[T](value: => T): Initialize[T] = pure(value _)
  def pure[T](value: () => T): Initialize[T] = new Value(value)
  def optional[T, U](i: Initialize[T])(f: Option[T] => U): Initialize[U] = new Optional(Some(i), f)

  def update[T](key: ScopedKey[T])(f: T => T): Setting[T] =
    setting[T](key, map(key)(f), NoPosition)

  def bind[S, T](in: Initialize[S])(f: S => Initialize[T]): Initialize[T] = new Bind(f, in)

  def map[S, T](in: Initialize[S])(f: S => T): Initialize[T] =
    new Apply[λ[L[x] => L[S]], T](f, in, AList.single[S])

  def app[K[L[x]], T](inputs: K[Initialize])(f: K[Id] => T)(
      implicit alist: AList[K]
  ): Initialize[T] = new Apply[K, T](f, inputs, alist)

  def uniform[S, T](inputs: Seq[Initialize[S]])(f: Seq[S] => T): Initialize[T] =
    new Apply[λ[L[x] => List[L[S]]], T](f, inputs.toList, AList.seq[S])

  /**
   * The result of this initialization is the validated `key`.
   * No dependency is introduced on `key`.  If `selfRefOk` is true, validation will not fail if the key is referenced by a definition of `key`.
   * That is, key := f(validated(key).value) is allowed only if `selfRefOk == true`.
   */
  private[sbt] final def validated[T](
      key: ScopedKey[T],
      selfRefOk: Boolean
  ): ValidationCapture[T] =
    new ValidationCapture(key, selfRefOk)

  /**
   * Constructs a derived setting that will be automatically defined in every scope where one of its dependencies
   * is explicitly defined and the where the scope matches `filter`.
   * A setting initialized with dynamic dependencies is only allowed if `allowDynamic` is true.
   * Only the static dependencies are tracked, however.  Dependencies on previous values do not introduce a derived setting either.
   */
  final def derive[T](
      s: Setting[T],
      allowDynamic: Boolean = false,
      filter: Scope => Boolean = const(true),
      trigger: AttributeKey[_] => Boolean = const(true),
      default: Boolean = false
  ): Setting[T] = {
    deriveAllowed(s, allowDynamic) foreach sys.error
    val d = new DerivedSetting[T](s.key, s.init, s.pos, filter, trigger)
    if (default) d.default() else d
  }

  def deriveAllowed[T](s: Setting[T], allowDynamic: Boolean): Option[String] = s.init match {
    case _: Bind[_, _] if !allowDynamic => Some("Cannot derive from dynamic dependencies.")
    case _                              => None
  }

  // id is used for equality
  private[sbt] final def defaultSetting[T](s: Setting[T]): Setting[T] = s.default()

  private[sbt] def defaultSettings(ss: Seq[Setting[_]]): Seq[Setting[_]] =
    ss.map(s => defaultSetting(s))

  private[this] final val nextID = new java.util.concurrent.atomic.AtomicLong
  private[this] final def nextDefaultID(): Long = nextID.incrementAndGet()

  def empty(implicit delegates: Scope => Seq[Scope]): Settings[Scope] =
    new Settings0(Map.empty, delegates)

  def asTransform(s: Settings[Scope]): ScopedKey ~> Id = λ[ScopedKey ~> Id](k => getValue(s, k))

  def getValue[T](s: Settings[Scope], k: ScopedKey[T]) =
    s.get(k.scope, k.key) getOrElse (throw new InvalidReference(k))

  def asFunction[T](s: Settings[Scope]): ScopedKey[T] => T = k => getValue(s, k)

  def mapScope(f: Scope => Scope): MapScoped = new MapScoped {
    def apply[T](k: ScopedKey[T]): ScopedKey[T] = k.copy(scope = f(k.scope))
  }

  private final class InvalidReference(val key: ScopedKey[_])
      extends RuntimeException(
        "Internal settings error: invalid reference to " + showFullKey.show(key)
      )

  private[this] def applyDefaults(ss: Seq[Setting[_]]): Seq[Setting[_]] = {
    val (defaults, others) = Util.separate[Setting[_], DefaultSetting[_], Setting[_]](ss) {
      case u: DefaultSetting[_] => Left(u); case s => Right(s)
    }
    defaults.distinct ++ others
  }

  def compiled(init: Seq[Setting[_]], actual: Boolean = true)(
      implicit delegates: Scope => Seq[Scope],
      scopeLocal: ScopeLocal,
      display: Show[ScopedKey[_]]
  ): CompiledMap = {
    val initDefaults = applyDefaults(init)
    // inject derived settings into scopes where their dependencies are directly defined
    // and prepend per-scope settings
    val derived = deriveAndLocal(initDefaults)
    // group by Scope/Key, dropping dead initializations
    val sMap: ScopedMap = grouped(derived)
    // delegate references to undefined values according to 'delegates'
    val dMap: ScopedMap = if (actual) delegate(sMap)(delegates, display) else sMap
    // merge Seq[Setting[_]] into Compiled
    compile(dMap)
  }

  def make(init: Seq[Setting[_]])(
      implicit delegates: Scope => Seq[Scope],
      scopeLocal: ScopeLocal,
      display: Show[ScopedKey[_]]
  ): Settings[Scope] = {
    val cMap = compiled(init)(delegates, scopeLocal, display)
    // order the initializations.  cyclic references are detected here.
    val ordered: Seq[Compiled[_]] = sort(cMap)
    // evaluation: apply the initializations.
    try { applyInits(ordered) } catch {
      case rru: RuntimeUndefined =>
        throw Uninitialized(cMap.keys.toSeq, delegates, rru.undefined, true)
    }
  }

  def sort(cMap: CompiledMap): Seq[Compiled[_]] =
    Dag.topologicalSort(cMap.values)(_.dependencies.map(cMap))

  def compile(sMap: ScopedMap): CompiledMap =
    sMap.toTypedSeq.map {
      case sMap.TPair(k, ss) =>
        val deps = ss.flatMap(_.dependencies).toSet
        (k, new Compiled(k, deps, ss))
    }.toMap

  def grouped(init: Seq[Setting[_]]): ScopedMap =
    ((IMap.empty: ScopedMap) /: init)((m, s) => add(m, s))

  def add[T](m: ScopedMap, s: Setting[T]): ScopedMap =
    m.mapValue[T](s.key, Nil, ss => append(ss, s))

  def append[T](ss: Seq[Setting[T]], s: Setting[T]): Seq[Setting[T]] =
    if (s.definitive) s :: Nil else ss :+ s

  def addLocal(init: Seq[Setting[_]])(implicit scopeLocal: ScopeLocal): Seq[Setting[_]] =
    init.flatMap(_.dependencies flatMap scopeLocal) ++ init

  def delegate(sMap: ScopedMap)(
      implicit delegates: Scope => Seq[Scope],
      display: Show[ScopedKey[_]]
  ): ScopedMap = {
    def refMap(ref: Setting[_], isFirst: Boolean) = new ValidateKeyRef {
      def apply[T](k: ScopedKey[T], selfRefOk: Boolean) =
        delegateForKey(sMap, k, delegates(k.scope), ref, selfRefOk || !isFirst)
    }

    type ValidatedSettings[T] = Either[Seq[Undefined], SettingSeq[T]]

    val f = λ[SettingSeq ~> ValidatedSettings] { (ks: Seq[Setting[_]]) =>
      val (undefs, valid) = Util.separate(ks.zipWithIndex) {
        case (s, i) => s validateKeyReferenced refMap(s, i == 0)
      }
      if (undefs.isEmpty) Right(valid) else Left(undefs.flatten)
    }

    type Undefs[_] = Seq[Undefined]
    val (undefineds, result) = sMap.mapSeparate[Undefs, SettingSeq](f)

    if (undefineds.isEmpty)
      result
    else
      throw Uninitialized(sMap.keys.toSeq, delegates, undefineds.values.flatten.toList, false)
  }

  private[this] def delegateForKey[T](
      sMap: ScopedMap,
      k: ScopedKey[T],
      scopes: Seq[Scope],
      ref: Setting[_],
      selfRefOk: Boolean
  ): Either[Undefined, ScopedKey[T]] = {
    val skeys = scopes.iterator.map(x => ScopedKey(x, k.key))
    val definedAt = skeys.find(sk => (selfRefOk || ref.key != sk) && (sMap contains sk))
    definedAt.toRight(Undefined(ref, k))
  }

  private[this] def applyInits(ordered: Seq[Compiled[_]])(
      implicit delegates: Scope => Seq[Scope]
  ): Settings[Scope] = {
    val x =
      java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)
    try {
      val eval: EvaluateSettings[Scope] = new EvaluateSettings[Scope] {
        override val init: Init.this.type = Init.this
        def compiledSettings = ordered
        def executor = x
      }
      eval.run
    } finally { x.shutdown() }
  }

  def showUndefined(u: Undefined, validKeys: Seq[ScopedKey[_]], delegates: Scope => Seq[Scope])(
      implicit display: Show[ScopedKey[_]]
  ): String = {
    val guessed = guessIntendedScope(validKeys, delegates, u.referencedKey)
    val derived = u.defining.isDerived
    val refString = display.show(u.defining.key)
    val sourceString = if (derived) "" else parenPosString(u.defining)
    val guessedString =
      if (derived) ""
      else guessed.map(g => "\n     Did you mean " + display.show(g) + " ?").toList.mkString
    val derivedString =
      if (derived) ", which is a derived setting that needs this key to be defined in this scope."
      else ""
    display.show(u.referencedKey) + " from " + refString + sourceString + derivedString + guessedString
  }

  private[this] def parenPosString(s: Setting[_]): String =
    s.positionString match { case None => ""; case Some(s) => " (" + s + ")" }

  def guessIntendedScope(
      validKeys: Seq[ScopedKey[_]],
      delegates: Scope => Seq[Scope],
      key: ScopedKey[_]
  ): Option[ScopedKey[_]] = {
    val distances = validKeys.flatMap { validKey =>
      refinedDistance(delegates, validKey, key).map(dist => (dist, validKey))
    }
    distances.sortBy(_._1).map(_._2).headOption
  }

  def refinedDistance(
      delegates: Scope => Seq[Scope],
      a: ScopedKey[_],
      b: ScopedKey[_]
  ): Option[Int] =
    if (a.key != b.key || a == b) None
    else {
      val dist = delegates(a.scope).indexOf(b.scope)
      if (dist < 0) None else Some(dist)
    }

  final class Uninitialized(val undefined: Seq[Undefined], override val toString: String)
      extends Exception(toString)

  final class Undefined private[sbt] (val defining: Setting[_], val referencedKey: ScopedKey[_])

  final class RuntimeUndefined(val undefined: Seq[Undefined])
      extends RuntimeException("References to undefined settings at runtime.") {
    override def getMessage =
      super.getMessage + undefined.map { u =>
        "\n" + u.defining + " referenced from " + u.referencedKey
      }.mkString
  }

  def Undefined(defining: Setting[_], referencedKey: ScopedKey[_]): Undefined =
    new Undefined(defining, referencedKey)

  def Uninitialized(
      validKeys: Seq[ScopedKey[_]],
      delegates: Scope => Seq[Scope],
      keys: Seq[Undefined],
      runtime: Boolean
  )(implicit display: Show[ScopedKey[_]]): Uninitialized = {
    assert(keys.nonEmpty)
    val suffix = if (keys.length > 1) "s" else ""
    val prefix = if (runtime) "Runtime reference" else "Reference"
    val keysString =
      keys.map(u => showUndefined(u, validKeys, delegates)).mkString("\n\n  ", "\n\n  ", "")
    new Uninitialized(
      keys,
      prefix + suffix + " to undefined setting" + suffix + ": " + keysString + "\n ")
  }

  final class Compiled[T](
      val key: ScopedKey[T],
      val dependencies: Iterable[ScopedKey[_]],
      val settings: Seq[Setting[T]]
  ) {
    override def toString = showFullKey.show(key)
  }

  final class Flattened(val key: ScopedKey[_], val dependencies: Iterable[ScopedKey[_]])

  def flattenLocals(compiled: CompiledMap): Map[ScopedKey[_], Flattened] = {
    val locals = compiled flatMap {
      case (key, comp) => if (key.key.isLocal) Seq[Compiled[_]](comp) else Nil
    }
    val ordered = Dag.topologicalSort(locals)(_.dependencies.flatMap(dep =>
      if (dep.key.isLocal) Seq[Compiled[_]](compiled(dep)) else Nil))
    def flatten(
        cmap: Map[ScopedKey[_], Flattened],
        key: ScopedKey[_],
        deps: Iterable[ScopedKey[_]]
    ): Flattened =
      new Flattened(
        key,
        deps.flatMap(dep => if (dep.key.isLocal) cmap(dep).dependencies else dep :: Nil))

    val empty = Map.empty[ScopedKey[_], Flattened]

    val flattenedLocals = (empty /: ordered) { (cmap, c) =>
      cmap.updated(c.key, flatten(cmap, c.key, c.dependencies))
    }

    compiled flatMap {
      case (key, comp) =>
        if (key.key.isLocal)
          Nil
        else
          Seq[(ScopedKey[_], Flattened)]((key, flatten(flattenedLocals, key, comp.dependencies)))
    }
  }

  def definedAtString(settings: Seq[Setting[_]]): String = {
    val posDefined = settings.flatMap(_.positionString.toList)
    if (posDefined.nonEmpty) {
      val header =
        if (posDefined.size == settings.size) "defined at:"
        else
          "some of the defining occurrences:"
      header + (posDefined.distinct mkString ("\n\t", "\n\t", "\n"))
    } else ""
  }

  /**
   * Intersects two scopes, returning the more specific one if they intersect, or None otherwise.
   */
  private[sbt] def intersect(s1: Scope, s2: Scope)(
      implicit delegates: Scope => Seq[Scope]): Option[Scope] =
    if (delegates(s1).contains(s2)) Some(s1) // s1 is more specific
    else if (delegates(s2).contains(s1)) Some(s2) // s2 is more specific
    else None

  private[this] def deriveAndLocal(init: Seq[Setting[_]])(
      implicit delegates: Scope => Seq[Scope],
      scopeLocal: ScopeLocal
  ): Seq[Setting[_]] = {
    import collection.mutable

    final class Derived(val setting: DerivedSetting[_]) {
      val dependencies = setting.dependencies.map(_.key)
      def triggeredBy = dependencies.filter(setting.trigger)
      val inScopes = new mutable.HashSet[Scope]
      val outputs = new mutable.ListBuffer[Setting[_]]
    }

    final class Deriveds(val key: AttributeKey[_], val settings: mutable.ListBuffer[Derived]) {
      def dependencies = settings.flatMap(_.dependencies)
      // This is mainly for use in the cyclic reference error message
      override def toString =
        s"Derived settings for ${key.label}, ${definedAtString(settings.map(_.setting))}"
    }

    // separate `derived` settings from normal settings (`defs`)
    val (derived, rawDefs) = Util.separate[Setting[_], Derived, Setting[_]](init) {
      case d: DerivedSetting[_] => Left(new Derived(d)); case s => Right(s)
    }
    val defs = addLocal(rawDefs)(scopeLocal)

    // group derived settings by the key they define
    val derivsByDef = new mutable.HashMap[AttributeKey[_], Deriveds]
    for (s <- derived) {
      val key = s.setting.key.key
      derivsByDef.getOrElseUpdate(key, new Deriveds(key, new mutable.ListBuffer)).settings += s
    }

    // index derived settings by triggering key.  This maps a key to the list of settings potentially derived from it.
    val derivedBy = new mutable.HashMap[AttributeKey[_], mutable.ListBuffer[Derived]]
    for (s <- derived; d <- s.triggeredBy)
      derivedBy.getOrElseUpdate(d, new mutable.ListBuffer) += s

    // Map a DerivedSetting[_] to the `Derived` struct wrapping it. Used to ultimately replace a DerivedSetting with
    // the `Setting`s that were actually derived from it: `Derived.outputs`
    val derivedToStruct: Map[DerivedSetting[_], Derived] = (derived map { s =>
      s.setting -> s
    }).toMap

    // set of defined scoped keys, used to ensure a derived setting is only added if all dependencies are present
    val defined = new mutable.HashSet[ScopedKey[_]]
    def addDefs(ss: Seq[Setting[_]]): Unit = { for (s <- ss) defined += s.key }
    addDefs(defs)

    // true iff the scoped key is in `defined`, taking delegation into account
    def isDefined(key: AttributeKey[_], scope: Scope) =
      delegates(scope).exists(s => defined.contains(ScopedKey(s, key)))

    // true iff all dependencies of derived setting `d` have a value (potentially via delegation) in `scope`
    def allDepsDefined(d: Derived, scope: Scope, local: Set[AttributeKey[_]]): Boolean =
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
    val deriveFor = (sk: ScopedKey[_]) => {
      val derivedForKey: List[Derived] = derivedBy.get(sk.key).toList.flatten
      val scope = sk.scope
      def localAndDerived(d: Derived): Seq[Setting[_]] = {
        def definingScope = d.setting.key.scope
        val outputScope = intersect(scope, definingScope)
        outputScope collect {
          case s if !d.inScopes.contains(s) && d.setting.filter(s) =>
            val local = d.dependencies.flatMap(dep => scopeLocal(ScopedKey(s, dep)))
            if (allDepsDefined(d, s, local.map(_.key.key).toSet)) {
              d.inScopes.add(s)
              val out = local :+ d.setting.setScope(s)
              d.outputs ++= out
              out
            } else
              Nil
        } getOrElse Nil
      }
      derivedForKey.flatMap(localAndDerived)
    }

    val processed = new mutable.HashSet[ScopedKey[_]]

    // derives settings, transitively so that a derived setting can trigger another
    def process(rem: List[Setting[_]]): Unit = rem match {
      case s :: ss =>
        val sk = s.key
        val ds = if (processed.add(sk)) deriveFor(sk) else Nil
        addDefs(ds)
        process(ds ::: ss)
      case Nil =>
    }
    process(defs.toList)

    // Take all the original defs and DerivedSettings along with locals, replace each DerivedSetting with the actual
    // settings that were derived.
    val allDefs = addLocal(init)(scopeLocal)
    allDefs flatMap {
      case d: DerivedSetting[_] => (derivedToStruct get d map (_.outputs)).toStream.flatten;
      case s                    => Stream(s)
    }
  }

  /** Abstractly defines a value of type `T`.
   *
   * Specifically it defines a node in a task graph,
   * where the `dependencies` represents dependent nodes,
   * and `evaluate` represents the calculation based on the existing body of knowledge.
   *
   * @tparam T the type of the value this defines.
   */
  sealed trait Initialize[T] {
    def dependencies: Seq[ScopedKey[_]]
    def apply[S](g: T => S): Initialize[S]

    private[sbt] def mapReferenced(g: MapScoped): Initialize[T]
    private[sbt] def mapConstant(g: MapConstant): Initialize[T]

    private[sbt] def validateReferenced(g: ValidateRef): ValidatedInit[T] =
      validateKeyReferenced(new ValidateKeyRef {
        def apply[B](key: ScopedKey[B], selfRefOk: Boolean) = g(key)
      })

    private[sbt] def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[T]

    def evaluate(map: Settings[Scope]): T
    def zip[S](o: Initialize[S]): Initialize[(T, S)] = zipTupled(o)(idFun)
    def zipWith[S, U](o: Initialize[S])(f: (T, S) => U): Initialize[U] = zipTupled(o)(f.tupled)
    private[this] def zipTupled[S, U](o: Initialize[S])(f: ((T, S)) => U): Initialize[U] =
      new Apply[λ[L[x] => (L[T], L[S])], U](f, (this, o), AList.tuple2[T, S])

    /** A fold on the static attributes of this and nested Initializes. */
    private[sbt] def processAttributes[S](init: S)(f: (S, AttributeMap) => S): S
  }

  object Initialize {
    implicit def joinInitialize[T](s: Seq[Initialize[T]]): JoinInitSeq[T] = new JoinInitSeq(s)

    final class JoinInitSeq[T](s: Seq[Initialize[T]]) {
      def joinWith[S](f: Seq[T] => S): Initialize[S] = uniform(s)(f)
      def join: Initialize[Seq[T]] = uniform(s)(idFun)
    }

    def join[T](inits: Seq[Initialize[T]]): Initialize[Seq[T]] = uniform(inits)(idFun)

    def joinAny[M[_]](inits: Seq[Initialize[M[T]] forSome { type T }]): Initialize[Seq[M[_]]] =
      join(inits.asInstanceOf[Seq[Initialize[M[_]]]])
  }

  object SettingsDefinition {
    implicit def unwrapSettingsDefinition(d: SettingsDefinition): Seq[Setting[_]] = d.settings
    implicit def wrapSettingsDefinition(ss: Seq[Setting[_]]): SettingsDefinition =
      new SettingList(ss)
  }

  sealed trait SettingsDefinition {
    def settings: Seq[Setting[_]]
  }

  final class SettingList(val settings: Seq[Setting[_]]) extends SettingsDefinition

  sealed class Setting[T] private[Init] (
      val key: ScopedKey[T],
      val init: Initialize[T],
      val pos: SourcePosition
  ) extends SettingsDefinition {
    def settings = this :: Nil
    def definitive: Boolean = !init.dependencies.contains(key)
    def dependencies: Seq[ScopedKey[_]] = remove(init.dependencies, key)
    def mapReferenced(g: MapScoped): Setting[T] = make(key, init mapReferenced g, pos)

    def validateReferenced(g: ValidateRef): Either[Seq[Undefined], Setting[T]] =
      (init validateReferenced g).right.map(newI => make(key, newI, pos))

    private[sbt] def validateKeyReferenced(g: ValidateKeyRef): Either[Seq[Undefined], Setting[T]] =
      (init validateKeyReferenced g).right.map(newI => make(key, newI, pos))

    def mapKey(g: MapScoped): Setting[T] = make(g(key), init, pos)
    def mapInit(f: (ScopedKey[T], T) => T): Setting[T] = make(key, init(t => f(key, t)), pos)
    def mapConstant(g: MapConstant): Setting[T] = make(key, init mapConstant g, pos)
    def withPos(pos: SourcePosition) = make(key, init, pos)

    def positionString: Option[String] = pos match {
      case pos: FilePosition => Some(pos.path + ":" + pos.startLine)
      case NoPosition        => None
    }

    private[sbt] def mapInitialize(f: Initialize[T] => Initialize[T]): Setting[T] =
      make(key, f(init), pos)

    override def toString = "setting(" + key + ") at " + pos

    protected[this] def make[B](
        key: ScopedKey[B],
        init: Initialize[B],
        pos: SourcePosition
    ): Setting[B] = new Setting[B](key, init, pos)

    protected[sbt] def isDerived: Boolean = false
    private[sbt] def setScope(s: Scope): Setting[T] =
      make(key.copy(scope = s), init.mapReferenced(mapScope(const(s))), pos)

    /** Turn this setting into a `DefaultSetting` if it's not already, otherwise returns `this` */
    private[sbt] def default(id: => Long = nextDefaultID()): DefaultSetting[T] =
      DefaultSetting(key, init, pos, id)
  }

  private[Init] sealed class DerivedSetting[T](
      sk: ScopedKey[T],
      i: Initialize[T],
      p: SourcePosition,
      val filter: Scope => Boolean,
      val trigger: AttributeKey[_] => Boolean
  ) extends Setting[T](sk, i, p) {

    override def make[B](key: ScopedKey[B], init: Initialize[B], pos: SourcePosition): Setting[B] =
      new DerivedSetting[B](key, init, pos, filter, trigger)

    protected[sbt] override def isDerived: Boolean = true

    override def default(_id: => Long): DefaultSetting[T] =
      new DerivedSetting[T](sk, i, p, filter, trigger) with DefaultSetting[T] { val id = _id }

    override def toString = "derived " + super.toString
  }

  // Only keep the first occurrence of this setting and move it to the front so that it has lower precedence than non-defaults.
  //  This is intended for internal sbt use only, where alternatives like Plugin.globalSettings are not available.
  private[Init] sealed trait DefaultSetting[T] extends Setting[T] {
    val id: Long

    override def make[B](key: ScopedKey[B], init: Initialize[B], pos: SourcePosition): Setting[B] =
      super.make(key, init, pos) default id

    override final def hashCode = id.hashCode

    override final def equals(o: Any): Boolean = o match {
      case d: DefaultSetting[_] => d.id == id; case _ => false
    }

    override def toString = s"default($id) " + super.toString
    override def default(id: => Long) = this
  }

  object DefaultSetting {
    def apply[T](sk: ScopedKey[T], i: Initialize[T], p: SourcePosition, _id: Long) =
      new Setting[T](sk, i, p) with DefaultSetting[T] { val id = _id }
  }

  private[this] def handleUndefined[T](vr: ValidatedInit[T]): Initialize[T] = vr match {
    case Left(undefs) => throw new RuntimeUndefined(undefs)
    case Right(x)     => x
  }

  private[this] lazy val getValidated = λ[ValidatedInit ~> Initialize](handleUndefined(_))

  // mainly for reducing generated class count
  private[this] def validateKeyReferencedT(g: ValidateKeyRef) =
    λ[Initialize ~> ValidatedInit](_ validateKeyReferenced g)

  private[this] def mapReferencedT(g: MapScoped) = λ[Initialize ~> Initialize](_ mapReferenced g)
  private[this] def mapConstantT(g: MapConstant) = λ[Initialize ~> Initialize](_ mapConstant g)
  private[this] def evaluateT(g: Settings[Scope]) = λ[Initialize ~> Id](_ evaluate g)

  private[this] def deps(ls: Seq[Initialize[_]]): Seq[ScopedKey[_]] = ls.flatMap(_.dependencies)

  /** An `Initialize[T]` associated with a `ScopedKey[S]`.
   * @tparam S the type of the associated `ScopedKey`
   * @tparam T the type of the value this `Initialize` defines.
   */
  sealed trait Keyed[S, T] extends Initialize[T] {
    def scopedKey: ScopedKey[S]
    def transform: S => T

    final def dependencies = scopedKey :: Nil
    final def apply[Z](g: T => Z): Initialize[Z] = new GetValue(scopedKey, g compose transform)
    final def evaluate(ss: Settings[Scope]): T = transform(getValue(ss, scopedKey))
    final def mapReferenced(g: MapScoped): Initialize[T] = new GetValue(g(scopedKey), transform)

    private[sbt] final def validateKeyReferenced(g: ValidateKeyRef): ValidatedInit[T] =
      g(scopedKey, false) match {
        case Left(un)  => Left(un :: Nil)
        case Right(nk) => Right(new GetValue(nk, transform))
      }

    final def mapConstant(g: MapConstant): Initialize[T] = g(scopedKey) match {
      case None        => this
      case Some(const) => new Value(() => transform(const))
    }

    private[sbt] def processAttributes[B](init: B)(f: (B, AttributeMap) => B): B = init
  }

  private[this] final class GetValue[S, T](val scopedKey: ScopedKey[S], val transform: S => T)
      extends Keyed[S, T]

  /** A `Keyed` where the type of the value and the associated `ScopedKey` are the same.
   * @tparam T the type of both the value this `Initialize` defines and the type of the associated `ScopedKey`.
   */
  trait KeyedInitialize[T] extends Keyed[T, T] {
    final val transform = idFun[T]
  }

  private[sbt] final class TransformCapture(val f: Initialize ~> Initialize)
      extends Initialize[Initialize ~> Initialize] {
    def dependencies = Nil
    def apply[Z](g2: (Initialize ~> Initialize) => Z): Initialize[Z] = map(this)(g2)
    def evaluate(ss: Settings[Scope]): Initialize ~> Initialize = f
    def mapReferenced(g: MapScoped) = new TransformCapture(mapReferencedT(g) ∙ f)
    def mapConstant(g: MapConstant) = new TransformCapture(mapConstantT(g) ∙ f)

    def validateKeyReferenced(g: ValidateKeyRef) =
      Right(new TransformCapture(getValidated ∙ validateKeyReferencedT(g) ∙ f))

    private[sbt] def processAttributes[S](init: S)(f: (S, AttributeMap) => S): S = init
  }

  private[sbt] final class ValidationCapture[T](val key: ScopedKey[T], val selfRefOk: Boolean)
      extends Initialize[ScopedKey[T]] {
    def dependencies = Nil
    def apply[Z](g2: ScopedKey[T] => Z): Initialize[Z] = map(this)(g2)
    def evaluate(ss: Settings[Scope]) = key
    def mapReferenced(g: MapScoped) = new ValidationCapture(g(key), selfRefOk)
    def mapConstant(g: MapConstant) = this

    def validateKeyReferenced(g: ValidateKeyRef) = g(key, selfRefOk) match {
      case Left(un) => Left(un :: Nil)
      case Right(k) => Right(new ValidationCapture(k, selfRefOk))
    }

    private[sbt] def processAttributes[S](init: S)(f: (S, AttributeMap) => S): S = init
  }

  private[sbt] final class Bind[S, T](val f: S => Initialize[T], val in: Initialize[S])
      extends Initialize[T] {
    def dependencies = in.dependencies
    def apply[Z](g: T => Z): Initialize[Z] = new Bind[S, Z](s => f(s)(g), in)
    def evaluate(ss: Settings[Scope]): T = f(in evaluate ss) evaluate ss
    def mapReferenced(g: MapScoped) = new Bind[S, T](s => f(s) mapReferenced g, in mapReferenced g)

    def validateKeyReferenced(g: ValidateKeyRef) = (in validateKeyReferenced g).right.map {
      validIn =>
        new Bind[S, T](s => handleUndefined(f(s) validateKeyReferenced g), validIn)
    }

    def mapConstant(g: MapConstant) = new Bind[S, T](s => f(s) mapConstant g, in mapConstant g)

    private[sbt] def processAttributes[B](init: B)(f: (B, AttributeMap) => B): B =
      in.processAttributes(init)(f)
  }

  private[sbt] final class Optional[S, T](val a: Option[Initialize[S]], val f: Option[S] => T)
      extends Initialize[T] {
    def dependencies = deps(a.toList)
    def apply[Z](g: T => Z): Initialize[Z] = new Optional[S, Z](a, g compose f)
    def mapReferenced(g: MapScoped) = new Optional(a map mapReferencedT(g).fn, f)

    def validateKeyReferenced(g: ValidateKeyRef) = a match {
      case None    => Right(this)
      case Some(i) => Right(new Optional(i.validateKeyReferenced(g).right.toOption, f))
    }

    def mapConstant(g: MapConstant): Initialize[T] = new Optional(a map mapConstantT(g).fn, f)
    def evaluate(ss: Settings[Scope]): T = f(a.flatMap(i => trapBadRef(evaluateT(ss)(i))))

    // proper solution is for evaluate to be deprecated or for external use only and a new internal method returning Either be used
    private[this] def trapBadRef[A](run: => A): Option[A] =
      try Some(run)
      catch { case _: InvalidReference => None }

    private[sbt] def processAttributes[B](init: B)(f: (B, AttributeMap) => B): B = a match {
      case None    => init
      case Some(i) => i.processAttributes(init)(f)
    }
  }

  private[sbt] final class Value[T](val value: () => T) extends Initialize[T] {
    def dependencies = Nil
    def mapReferenced(g: MapScoped) = this
    def validateKeyReferenced(g: ValidateKeyRef) = Right(this)
    def apply[S](g: T => S) = new Value[S](() => g(value()))
    def mapConstant(g: MapConstant) = this
    def evaluate(map: Settings[Scope]): T = value()
    private[sbt] def processAttributes[S](init: S)(f: (S, AttributeMap) => S): S = init
  }

  private[sbt] final object StaticScopes extends Initialize[Set[Scope]] {
    def dependencies = Nil
    def mapReferenced(g: MapScoped) = this
    def validateKeyReferenced(g: ValidateKeyRef) = Right(this)
    def apply[S](g: Set[Scope] => S) = map(this)(g)
    def mapConstant(g: MapConstant) = this
    def evaluate(map: Settings[Scope]) = map.scopes
    private[sbt] def processAttributes[S](init: S)(f: (S, AttributeMap) => S): S = init
  }

  private[sbt] final class Apply[K[L[x]], T](
      val f: K[Id] => T,
      val inputs: K[Initialize],
      val alist: AList[K]
  ) extends Initialize[T] {
    def dependencies = deps(alist.toList(inputs))
    def mapReferenced(g: MapScoped) = mapInputs(mapReferencedT(g))
    def apply[S](g: T => S) = new Apply(g compose f, inputs, alist)
    def mapConstant(g: MapConstant) = mapInputs(mapConstantT(g))

    def mapInputs(g: Initialize ~> Initialize): Initialize[T] =
      new Apply(f, alist.transform(inputs, g), alist)

    def evaluate(ss: Settings[Scope]) = f(alist.transform(inputs, evaluateT(ss)))

    def validateKeyReferenced(g: ValidateKeyRef) = {
      val tx = alist.transform(inputs, validateKeyReferencedT(g))
      val undefs = alist.toList(tx).flatMap(_.left.toSeq.flatten)
      val get = λ[ValidatedInit ~> Initialize](_.right.get)
      if (undefs.isEmpty) Right(new Apply(f, alist.transform(tx, get), alist)) else Left(undefs)
    }

    private[sbt] def processAttributes[S](init: S)(f: (S, AttributeMap) => S): S =
      (init /: alist.toList(inputs)) { (v, i) =>
        i.processAttributes(v)(f)
      }
  }
  private def remove[T](s: Seq[T], v: T) = s filterNot (_ == v)
}
