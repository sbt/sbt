package sbt

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executor
import Types.Id
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Promise }

sealed trait EvaluationState[+T]

object EvaluationState {
  /** New */
  object New extends EvaluationState[Nothing]

  /** Being evaluated */
  object Started extends EvaluationState[Nothing]

  /** Being evaluated */
  class Blocked(initial: Int) extends EvaluationState[Nothing] {
    val count = new AtomicInteger(initial)
  }

  /** Evaluation finished */
  class Evaluated[+T](val value: T) extends EvaluationState[T]
}

final class EvaluateExecutor(executor: Executor) {
  private[this] val result = Promise[Unit]()

  private[this] val runningCount = new AtomicInteger

  private[this] def runnable(work: => Unit) = new Runnable {
    runningCount.incrementAndGet()
    def run() = if (!result.isCompleted) {
      try {
        work
        if (runningCount.decrementAndGet() == 0) result.trySuccess(())
      } catch {
        case e: Throwable => result.tryFailure(e)
      }
    }
  }

  /** Execute the work, and wait until all submitted work has finished. May be called only once. */
  def evaluate(work: => Unit)(duration: Duration): Unit = {
    assert(runningCount.get == 0 && !result.isCompleted, "evaluate() already called")
    runnable(work).run()
    Await.result(result.future, duration)
  }

  /** Execute the work asynchronously */
  def submit(work: => Unit): Unit = executor.execute(runnable(work))
}

abstract class EvaluateSettings[Scope] {
  protected[this] val init: Init[Scope]
  import init._
  protected[this] def executor: Executor

  private[this] val evaluateExecutor = new EvaluateExecutor(executor)

  protected[this] def compiledSettings: Seq[Compiled[_]]

  import EvaluationState._

  private[this] val static = PMap.empty[ScopedKey, INode]
  private[this] val allScopes: Set[Scope] = compiledSettings.map(_.key.scope).toSet

  private[this] val transform: Initialize ~> INode = new (Initialize ~> INode) {
    def apply[T](i: Initialize[T]): INode[T] = i match {
      case k: Keyed[s, T] @unchecked => static.get(k.scopedKey).fold(sys.error(s"Illegal reference to key ${k.scopedKey}"))(single(_, k.transform))
      case a: Apply[k, T] @unchecked =>
        val a0 = a.asInstanceOf[Apply[({ type a[b[c]] = Any })#a, T]] // scalac fails to realize that k is higher-kinded
        mixed(a0.alist.transform(a0.inputs, this), a0.f, a0.alist)
      case b: Bind[s, T] @unchecked           => bind(apply(b.in), (x: s) => apply(b.f(x)))
      case StaticScopes                       => strictConstant(allScopes.asInstanceOf[T]) // cast since T is unbounded; TODO: fix types so this isn't just assumed
      case v: Value[T] @unchecked             => constant(v.value)
      case v: ValidationCapture[T] @unchecked => strictConstant(v.key)
      case t: TransformCapture                => strictConstant(t.f)
      case o: Optional[s, T] @unchecked       => o.a.fold(strictConstant(o.f(None)))(i => single(apply(i), (x: s) => o.f(Some(x))))
    }
  }
  private[this] lazy val roots: Seq[INode[_]] = compiledSettings flatMap { cs =>
    cs.settings map { s =>
      val t = transform(s.init)
      static(s.key) = t
      t: INode[_]
    }
  }

  final def run(implicit delegates: Scope => Seq[Scope]): Settings[Scope] =
    {
      evaluateExecutor.evaluate(roots.foreach(_.start()))(Duration.Inf)
      (empty /: static.toTypedSeq) {
        case (ss, static.TPair(key, node)) => if (key.key.isLocal) ss else ss.set(key.scope, key.key, node.value)
      }
    }

  private[this] val getValue = new (INode ~> Id) { def apply[T](node: INode[T]) = node.value }

  private[this] sealed abstract class INode[T] {
    self =>

    /** Current state */
    private[this] var state: EvaluationState[T] = New

    /** Handlers to call once evaluation is complete. */
    private[this] val completeCallbacks = new collection.mutable.ListBuffer[() => Unit]

    override def toString = {
      val keyString = static.toSeq.collectFirst { case (key, `self`) => init.showFullKey(key) } getOrElse "non-static"
      s"${getClass.getName} (state=$state,onComplete=${completeCallbacks.size}): $keyString"
    }

    /** Get the evaluated value. May be called only when evaluation has completed. */
    final def value: T = state.asInstanceOf[Evaluated[T]].value

    /** Set the evaluated value. May be called only once. */
    final protected[this] def value_=(value: T) = {
      assert(!state.isInstanceOf[Evaluated[_]], s"value_=() already called: $this")
      val result = new Evaluated(value)
      synchronized(state = result)
      completeCallbacks foreach (_())
      completeCallbacks.clear()
    }

    /** Start evaluation, if not started. */
    final def start(): Unit = {
      val isNew = synchronized {
        val isNew = state eq New
        if (isNew) state = Started
        isNew
      }
      if (isNew) {
        val dependencies = dependsOn
        if (dependencies.isEmpty) {
          evaluateExecutor.submit(evaluate())
        } else {
          val blocked = new Blocked(dependencies.size)
          state = blocked
          dependencies.foreach(_.onComplete(
            if (blocked.count.decrementAndGet() == 0) evaluateExecutor.submit(evaluate())
          ))
        }
      }
    }

    /** Call the callback when evaluation is complete. */
    final def onComplete(callback: => Unit) = {
      val evaluated = synchronized {
        val evaluated = state.isInstanceOf[Evaluated[_]]
        if (!evaluated) {
          completeCallbacks += (() => callback)
        }
        evaluated
      }
      if (evaluated) callback else start()
    }

    /** Dependencies */
    protected def dependsOn: Iterable[INode[_]]

    /** Perform the evaluation. Implementations should eventually call [[value_=()]]. */
    protected def evaluate(): Unit
  }

  private[this] def strictConstant[T](v: T): INode[T] = new INode[T] {
    protected[this] def dependsOn = Nil
    protected[this] def evaluate() = value = v
  }

  private[this] def constant[T](f: () => T): INode[T] = new INode[T] {
    protected[this] def dependsOn = Nil
    protected[this] def evaluate() = value = f()
  }

  private[this] def single[S, T](in: INode[S], f: S => T): INode[T] = new INode[T] {
    protected[this] def dependsOn = in :: Nil
    protected[this] def evaluate() = value = f(in.value)
  }

  private[this] final def mixed[K[_[_]], T](in: K[INode], f: K[Id] => T, alist: AList[K]): INode[T] = new INode[T] {
    protected[this] def dependsOn = alist.toList(in)
    protected[this] def evaluate() = value = f(alist.transform(in, getValue))
  }

  private[this] final def bind[S, T](in: INode[S], f: S => INode[T]): INode[T] = new INode[T] {
    protected[this] def dependsOn = in :: Nil
    protected[this] def evaluate() = {
      val node = f(in.value)
      node.onComplete(evaluateExecutor.submit { value = node.value }) // submit prevents StackOverflowError
    }
  }
}
