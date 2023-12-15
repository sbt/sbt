/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.lang.Runnable
import java.util.concurrent.{ atomic, Executor, LinkedBlockingQueue }
import atomic.{ AtomicBoolean, AtomicInteger }

enum EvaluationState:
  case New
  case Blocked
  case Ready
  case Calling
  case Evaluated

abstract class EvaluateSettings[ScopeType]:
  protected val init: Init[ScopeType]
  import init._

  protected def executor: Executor
  protected def compiledSettings: Seq[Compiled[_]]

  import EvaluationState.*

  private[this] val complete = new LinkedBlockingQueue[Option[Throwable]]
  private[this] val static = PMap.empty[ScopedKey, INode]
  private[this] val allScopes: Set[ScopeType] = compiledSettings.map(_.key.scope).toSet

  private[this] def getStatic[A](key: ScopedKey[A]): INode[A] =
    static.get(key).getOrElse { sys.error("Illegal reference to key " + key) }

  private[this] val transform: [A] => Initialize[A] => INode[A] = [A] =>
    (fa: Initialize[A]) =>
      fa match
        case k: Keyed[s, A]   => single(getStatic(k.scopedKey), k.transform)
        case u: Uniform[s, A] => UniformNode(u.inputs.map(transform[s]), u.f)
        case a: Apply[k, A] =>
          MixedNode[k, A](TupleMapExtension.transform(a.inputs) { transform }, a.f)
        case b: Bind[s, A]           => BindNode[s, A](transform(b.in), x => transform(b.f(x)))
        case v: Value[A]             => constant(v.value)
        case v: ValidationCapture[a] => strictConstant(v.key: A)
        case t: TransformCapture     => strictConstant(t.f: A)
        case o: Optional[s, A] =>
          o.a match
            case None    => constant(() => o.f(None))
            case Some(i) => single[s, A](transform(i), x => o.f(Some(x)))
        case StaticScopes => strictConstant(allScopes)

  private[this] lazy val roots: Seq[INode[_]] = compiledSettings.flatMap { cs =>
    (cs.settings map { s =>
      val t = transform(s.init)
      static(s.key) = t
      t
    }): Seq[INode[_]]
  }

  private[this] val running = new AtomicInteger
  private[this] val cancel = new AtomicBoolean(false)

  def run(implicit delegates: ScopeType => Seq[ScopeType]): Settings[ScopeType] = {
    assert(running.get() == 0, "Already running")
    startWork()
    roots.foreach(_.registerIfNew())
    workComplete()
    complete.take() foreach { ex =>
      cancel.set(true)
      throw ex
    }
    getResults(delegates)
  }

  private[this] def getResults(implicit delegates: ScopeType => Seq[ScopeType]) =
    static.toTypedSeq.foldLeft(empty) { case (ss, static.TPair(key, node)) =>
      if key.key.isLocal then ss
      else ss.set(key.scope, key.key, node.get)
    }

  private[this] lazy val getValue: [A] => INode[A] => A = [A] => (fa: INode[A]) => fa.get

  private[this] def submitEvaluate(node: INode[_]) = submit(node.evaluate())

  private[this] def submitCallComplete[A](node: BindNode[_, A], value: A) =
    submit(node.callComplete(value))

  private[this] def submit(work: => Unit): Unit =
    startWork()
    // new Runnable { def run = if (!cancel.get()) run0(work) }
    executor.execute(() => if !cancel.get() then run0(work) else ())

  private[this] def run0(work: => Unit): Unit =
    try {
      work
    } catch { case e: Throwable => complete.put(Some(e)) }
    workComplete()

  private[this] def startWork(): Unit = { running.incrementAndGet(); () }

  private[this] def workComplete(): Unit =
    if running.decrementAndGet() == 0 then complete.put(None)
    else ()

  private[this] sealed abstract class INode[A1]:
    private[this] var state: EvaluationState = New
    private[this] var value: A1 = _
    private[this] val blocking = new collection.mutable.ListBuffer[INode[_]]
    private[this] var blockedOn: Int = 0
    private[this] val calledBy = new collection.mutable.ListBuffer[BindNode[_, A1]]

    override def toString(): String =
      getClass.getName + " (state=" + state + ",blockedOn=" + blockedOn + ",calledBy=" + calledBy.size + ",blocking=" + blocking.size + "): " +
        keyString

    private[this] def keyString =
      (static.toSeq.flatMap { case (key, value) =>
        if (value eq this) init.showFullKey.show(key) :: Nil else List.empty[String]
      }).headOption getOrElse "non-static"

    final def get: A1 = synchronized {
      assert(value != null, toString + " not evaluated")
      value
    }

    final def doneOrBlock(from: INode[_]): Boolean = synchronized {
      val ready = state == Evaluated
      if (!ready) {
        blocking += from
        ()
      }
      registerIfNew()
      ready
    }

    final def isDone: Boolean = synchronized { state == Evaluated }
    final def isNew: Boolean = synchronized { state == New }
    final def isCalling: Boolean = synchronized { state == Calling }
    final def registerIfNew(): Unit = synchronized { if (state == New) register() }

    private[this] def register(): Unit = {
      assert(state == New, "Already registered and: " + toString)
      val deps = dependsOn
      blockedOn = deps.size - deps.count(_.doneOrBlock(this))
      if blockedOn == 0 then schedule()
      else state = Blocked
    }

    final def schedule(): Unit = synchronized {
      assert(state == New || state == Blocked, "Invalid state for schedule() call: " + toString)
      state = Ready
      submitEvaluate(this)
    }

    final def unblocked(): Unit = synchronized {
      assert(state == Blocked, "Invalid state for unblocked() call: " + toString)
      blockedOn -= 1
      assert(blockedOn >= 0, "Negative blockedOn: " + blockedOn + " for " + toString)
      if (blockedOn == 0) schedule()
    }

    final def evaluate(): Unit = synchronized { evaluate0() }

    protected final def makeCall(source: BindNode[_, A1], target: INode[A1]): Unit = {
      assert(state == Ready, "Invalid state for call to makeCall: " + toString)
      state = Calling
      target.call(source)
    }

    protected final def setValue(v: A1): Unit = {
      assert(
        state != Evaluated,
        "Already evaluated (trying to set value to " + v + "): " + toString
      )
      if (v == null) sys.error("Setting value cannot be null: " + keyString)
      value = v
      state = Evaluated
      blocking foreach { _.unblocked() }
      blocking.clear()
      calledBy foreach { node =>
        submitCallComplete(node, value)
      }
      calledBy.clear()
    }

    final def call(by: BindNode[_, A1]): Unit = synchronized {
      registerIfNew()
      state match {
        case Evaluated => submitCallComplete(by, value)
        case _ =>
          calledBy += by
          ()
      }
    }

    protected def dependsOn: Seq[INode[_]]
    protected def evaluate0(): Unit
  end INode

  private[this] def strictConstant[A1](v: A1): INode[A1] = constant(() => v)

  private[this] def constant[A1](f: () => A1): INode[A1] =
    MixedNode[EmptyTuple, A1](EmptyTuple, _ => f())

  private[this] def single[A1, A2](in: INode[A1], f: A1 => A2): INode[A2] =
    MixedNode[Tuple1[A1], A2](Tuple1(in), { case Tuple1(a) => f(a) })

  private[this] final class BindNode[A1, A2](in: INode[A1], f: A1 => INode[A2]) extends INode[A2]:
    protected def dependsOn: Seq[INode[_]] = in :: Nil
    protected def evaluate0(): Unit = makeCall(this, f(in.get))
    def callComplete(value: A2): Unit = synchronized {
      assert(isCalling, "Invalid state for callComplete(" + value + "): " + toString)
      setValue(value)
    }
  end BindNode

  private[this] final class MixedNode[Tup <: Tuple, A1](in: Tuple.Map[Tup, INode], f: Tup => A1)
      extends INode[A1]:
    import TupleMapExtension.*
    protected override def dependsOn: Seq[INode[_]] = in.iterator.toList
    protected override def evaluate0(): Unit = setValue(f(in.unmap(getValue)))

  private[this] final class UniformNode[A1, A2](in: List[INode[A1]], f: List[A1] => A2)
      extends INode[A2]:
    protected override def dependsOn: Seq[INode[_]] = in
    protected override def evaluate0(): Unit = setValue(f(in.map(_.get)))

end EvaluateSettings
