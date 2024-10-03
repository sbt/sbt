/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.ExecutionException

import sbt.internal.util.ErrorHandling.wideConvert
import sbt.internal.util.{ DelegatingPMap, IDSet, PMap, RMap }
import sbt.internal.util.Types.const
import sbt.internal.util.Util.nilSeq

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import mutable.Map

private[sbt] object Execute {
  def taskMap[A]: Map[TaskId[?], A] = (new java.util.IdentityHashMap[TaskId[?], A]).asScala
  def taskPMap[F[_]]: PMap[TaskId, F] = new DelegatingPMap(
    (new java.util.IdentityHashMap[TaskId[Any], F[Any]]).asScala
  )
  private[sbt] def completed(p: => Unit): Completed = new Completed {
    def process(): Unit = p
  }
  def noTriggers[TaskId[_]] = new Triggers(Map.empty, Map.empty, identity)

  def config(checkCycles: Boolean, overwriteNode: Incomplete => Boolean = const(false)): Config =
    new Config(checkCycles, overwriteNode)
  final class Config private[sbt] (
      val checkCycles: Boolean,
      val overwriteNode: Incomplete => Boolean
  )

  final val checkPreAndPostConditions =
    sys.props.get("sbt.execute.extrachecks").exists(java.lang.Boolean.parseBoolean)
}

sealed trait Completed {
  def process(): Unit
}

private[sbt] trait NodeView {
  def apply[A](a: TaskId[A]): Node[A]
  def inline1[A](a: TaskId[A]): Option[() => A]
}

final class Triggers(
    val runBefore: collection.Map[TaskId[?], Seq[TaskId[?]]],
    val injectFor: collection.Map[TaskId[?], Seq[TaskId[?]]],
    val onComplete: RMap[TaskId, Result] => RMap[TaskId, Result],
)

private[sbt] final class Execute(
    config: Execute.Config,
    triggers: Triggers,
    progress: ExecuteProgress
)(using view: NodeView) {
  import Execute.*

  private val forward = taskMap[IDSet[TaskId[?]]]
  private val reverse = taskMap[Iterable[TaskId[?]]]
  private val callers = taskPMap[[X] =>> IDSet[TaskId[X]]]
  private val state = taskMap[State]
  private val viewCache = taskPMap[Node]
  private val results = taskPMap[Result]

  private val getResult: [A] => TaskId[A] => Result[A] = [A] =>
    (a: TaskId[A]) =>
      view.inline1(a) match
        case Some(v) => Result.Value(v())
        case None    => results(a)
  private type State = State.Value
  private object State extends Enumeration {
    val Pending, Running, Calling, Done = Value
  }
  import State.{ Pending, Running, Calling, Done }

  val init = progress.initial()

  def dump: String =
    "State: " + state.toString + "\n\nResults: " + results + "\n\nCalls: " + callers + "\n\n"

  def run[A](root: TaskId[A])(using strategy: CompletionService): Result[A] =
    try {
      runKeep(root)(root)
    } catch { case i: Incomplete => Result.Inc(i) }

  def runKeep[A](root: TaskId[A])(using strategy: CompletionService): RMap[TaskId, Result] = {
    assert(state.isEmpty, "Execute already running/ran.")

    addNew(root)
    processAll()
    assert(results contains root, "No result for root node.")
    val finalResults = triggers.onComplete(results)
    progress.afterAllCompleted(finalResults)
    progress.stop()
    finalResults
  }

  def processAll()(using strategy: CompletionService): Unit = {
    @tailrec def next(): Unit = {
      pre {
        assert(reverse.nonEmpty, "Nothing to process.")
        if (!state.values.exists(_ == Running)) {
          snapshotCycleCheck()
          assert(
            false,
            "Internal task engine error: nothing running.  This usually indicates a cycle in tasks.\n  Calling tasks (internal task engine state):\n" + dumpCalling
          )
        }
      }

      try {
        strategy.take().process()
      } catch {
        case e: ExecutionException =>
          e.getCause match {
            case oom: OutOfMemoryError => throw oom
            case _                     => throw e
          }
      }
      if (reverse.nonEmpty) next()
    }
    next()

    post {
      assert(reverse.isEmpty, "Did not process everything.")
      assert(complete, "Not all state was Done.")
    }
  }
  def dumpCalling: String = state.filter(_._2 == Calling).mkString("\n\t")

  def call[A](node: TaskId[A], target: TaskId[A])(using strategy: CompletionService): Unit = {
    if (config.checkCycles) cycleCheck(node, target)
    pre {
      assert(running(node))
      readyInv(node)
    }

    results.get(target) match {
      case Some(result) => retire(node, result)
      case None =>
        state(node) = Calling
        addChecked(target)
        addCaller(node, target)
    }

    post {
      if (done(target)) assert(done(node))
      else {
        assert(calling(node))
        assert(callers(target) contains node)
      }
      readyInv(node)
    }
  }

  def retire[A](node: TaskId[A], result: Result[A])(using strategy: CompletionService): Unit = {
    pre {
      assert(running(node) | calling(node))
      readyInv(node)
    }

    results(node) = result
    state(node) = Done
    progress.afterCompleted(node, result)
    remove(reverse, node).foreach(dep => notifyDone(node, dep))
    callers.remove(node).toList.flatten.foreach { c =>
      retire(c, callerResult(c, result))
    }
    triggeredBy(node) foreach { t =>
      addChecked(t)
    }

    post {
      assert(done(node))
      assert(results(node) == result)
      readyInv(node)
      assert(!(reverse.contains(node)))
      assert(!(callers.contains(node)))
      assert(triggeredBy(node) forall added)
    }
  }
  def callerResult[A](node: TaskId[A], result: Result[A]): Result[A] =
    result match {
      case _: Result.Value[A] => result
      case Result.Inc(i)      => Result.Inc(Incomplete(Some(node), tpe = i.tpe, causes = i :: Nil))
    }

  def notifyDone(node: TaskId[?], dependent: TaskId[?])(using strategy: CompletionService): Unit = {
    val f = forward(dependent)
    f -= node
    if (f.isEmpty) {
      remove(forward, dependent)
      ready(dependent)
    }
  }

  /**
   * Ensures the given node has been added to the system. Once added, a node is pending until its
   * inputs and dependencies have completed. Its computation is then evaluated and made available
   * for nodes that have it as an input.
   */
  def addChecked[A](node: TaskId[A])(using strategy: CompletionService): Unit = {
    if (!added(node)) addNew(node)

    post { addedInv(node) }
  }

  /**
   * Adds a node that has not yet been registered with the system. If all of the node's dependencies
   * have finished, the node's computation is scheduled to run. The node's dependencies will be
   * added (transitively) if they are not already registered.
   */
  def addNew(node: TaskId[?])(using strategy: CompletionService): Unit = {
    pre { newPre(node) }

    val v = register(node)
    val deps = dependencies(v) ++ runBefore(node)
    val active = IDSet[TaskId[?]](deps filter notDone)
    progress.afterRegistered(
      node,
      deps,
      active.toList
      /* active is mutable, so take a snapshot */
    )

    if (active.isEmpty) ready(node)
    else {
      forward(node) = active
      for (a <- active) {
        addChecked(a)
        addReverse(a, node)
      }
    }

    post {
      addedInv(node)
      assert(running(node) ^ pending(node))
      if (running(node)) runningInv(node)
      if (pending(node)) pendingInv(node)
    }
  }

  /**
   * Called when a pending 'node' becomes runnable. All of its dependencies must be done. This
   * schedules the node's computation with 'strategy'.
   */
  def ready(node: TaskId[?])(using strategy: CompletionService): Unit = {
    pre {
      assert(pending(node))
      readyInv(node)
      assert(reverse.contains(node))
    }

    state(node) = Running
    progress.afterReady(node)
    submit(node)

    post {
      readyInv(node)
      assert(reverse.contains(node))
      assert(running(node))
    }
  }

  /** Enters the given node into the system. */
  def register[A](node: TaskId[A]): Node[A] = {
    state(node) = Pending
    reverse(node) = Seq()
    viewCache.getOrUpdate(node, view(node))
  }

  /** Send the work for this node to the provided Strategy. */
  def submit(node: TaskId[?])(using strategy: CompletionService): Unit = {
    val v = viewCache(node)
    val rs = v.computeInputs(getResult)
    strategy.submit(node, () => work(node, v.work(rs)))
  }

  /**
   * Evaluates the computation 'f' for 'node'. This returns a Completed instance, which contains the
   * post-processing to perform after the result is retrieved from the Strategy.
   */
  def work[A](node: TaskId[A], f: => Either[TaskId[A], A])(using
      strategy: CompletionService
  ): Completed = {
    progress.beforeWork(node)
    val rawResult = wideConvert(f).left.map {
      case i: Incomplete => if (config.overwriteNode(i)) i.copy(node = Some(node)) else i
      case e             => Incomplete(Some(node), Incomplete.Error, directCause = Some(e))
    }
    val result = rewrap(rawResult)
    progress.afterWork(node, result)
    completed {
      result match {
        case Right(v)     => retire(node, v)
        case Left(target) => call(node, target)
      }
    }
  }
  private def rewrap[A](
      rawResult: Either[Incomplete, Either[TaskId[A], A]]
  ): Either[TaskId[A], Result[A]] =
    rawResult match {
      case Left(i)             => Right(Result.Inc(i))
      case Right(Right(v))     => Right(Result.Value(v))
      case Right(Left(target)) => Left(target)
    }

  def remove[K, V](map: Map[K, V], k: K): V =
    map.remove(k).getOrElse(sys.error("Key '" + k + "' not in map :\n" + map))

  def addReverse(node: TaskId[?], dependent: TaskId[?]): Unit =
    reverse(node) ++= Seq(dependent)
  def addCaller[A](caller: TaskId[A], target: TaskId[A]): Unit =
    callers.getOrUpdate(target, IDSet.create) += caller

  def dependencies(node: TaskId[?]): Iterable[TaskId[?]] = dependencies(viewCache(node))
  def dependencies(v: Node[?]): Iterable[TaskId[?]] =
    v.dependencies.filter(dep => view.inline1(dep).isEmpty)

  def runBefore(node: TaskId[?]): Seq[TaskId[?]] = triggers.runBefore.getOrElse(node, nilSeq)
  def triggeredBy(node: TaskId[?]): Seq[TaskId[?]] = triggers.injectFor.getOrElse(node, nilSeq)

  // Contracts

  def addedInv(node: TaskId[?]): Unit = topologicalSort(node).foreach(addedCheck)
  def addedCheck(node: TaskId[?]): Unit = {
    assert(added(node), "Not added: " + node)
    assert(viewCache.contains(node), "Not in view cache: " + node)
    dependencyCheck(node)
  }
  def dependencyCheck(node: TaskId[?]): Unit = {
    dependencies(node) foreach { dep =>
      def onOpt[A](o: Option[A])(f: A => Boolean) = o match {
        case None => false; case Some(x) => f(x)
      }
      def checkForward = onOpt(forward.get(node))(_.contains(dep))
      def checkReverse = onOpt(reverse.get(dep))(_.exists(_ == node))
      assert(done(dep) ^ (checkForward && checkReverse))
    }
  }
  def pendingInv(node: TaskId[?]): Unit = {
    assert(atState(node, Pending))
    assert((dependencies(node) ++ runBefore(node)).exists(notDone))
  }
  def runningInv(node: TaskId[?]): Unit = {
    assert(dependencies(node).forall(done))
    assert(!(forward.contains(node)))
  }
  def newPre(node: TaskId[?]): Unit = {
    isNew(node)
    assert(!(reverse.contains(node)))
    assert(!(forward.contains(node)))
    assert(!(callers.contains(node)))
    assert(!(viewCache.contains(node)))
    assert(!(results.contains(node)))
  }

  def topologicalSort(node: TaskId[?]): Seq[TaskId[?]] = {
    val seen = IDSet.create[TaskId[?]]
    def visit(n: TaskId[?]): List[TaskId[?]] =
      seen.process(n)(List.empty) {
        val deps: List[TaskId[?]] =
          dependencies(n).foldLeft(List.empty)((ss, dep) => visit(dep) ::: ss)
        node :: deps
      }

    visit(node).reverse
  }

  def readyInv(node: TaskId[?]): Unit = {
    assert(dependencies(node).forall(done))
    assert(!(forward.contains(node)))
  }

  // cyclic reference checking

  def snapshotCycleCheck(): Unit =
    callers.toSeq foreach { case (called, callers) =>
      for (caller <- callers) cycleCheck(caller, called)
    }

  def cycleCheck(node: TaskId[?], target: TaskId[?]): Unit = {
    if (node eq target) cyclic(node, target, "Cannot call self")
    val all = IDSet.create[TaskId[?]]
    def allCallers(n: TaskId[?]): Unit = (all process n)(()) {
      callers.get(n).toList.flatten.foreach(allCallers)
    }
    allCallers(node)
    if (all contains target) cyclic(node, target, "Cyclic reference")
  }
  def cyclic(caller: TaskId[?], target: TaskId[?], msg: String) =
    throw new Incomplete(
      Some(caller),
      message = Some(msg),
      directCause = Some(new CyclicException(caller, target, msg))
    )
  final class CyclicException(val caller: TaskId[?], val target: TaskId[?], msg: String)
      extends Exception(msg)

  // state testing

  def pending(d: TaskId[?]) = atState(d, Pending)
  def running(d: TaskId[?]) = atState(d, Running)
  def calling(d: TaskId[?]) = atState(d, Calling)
  def done(d: TaskId[?]) = atState(d, Done)
  def notDone(d: TaskId[?]) = !done(d)
  private def atState(d: TaskId[?], s: State) = state.get(d) == Some(s)
  def isNew(d: TaskId[?]) = !added(d)
  def added(d: TaskId[?]) = state.contains(d)
  def complete = state.values.forall(_ == Done)

  def pre(f: => Unit) = if (checkPreAndPostConditions) f
  def post(f: => Unit) = if (checkPreAndPostConditions) f
}
