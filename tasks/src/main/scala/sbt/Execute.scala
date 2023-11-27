/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.ExecutionException

import sbt.internal.util.ErrorHandling.wideConvert
import sbt.internal.util.{ DelegatingPMap, IDSet, PMap, RMap, ~> }
import sbt.internal.util.Types.*
import sbt.internal.util.Util.nilSeq

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import mutable.Map
import sbt.internal.util.AList

private[sbt] object Execute {
  def taskMap[A]: Map[Task[?], A] = (new java.util.IdentityHashMap[Task[?], A]).asScala
  def taskPMap[F[_]]: PMap[Task, F] = new DelegatingPMap(
    (new java.util.IdentityHashMap[Task[Any], F[Any]]).asScala
  )
  private[sbt] def completed(p: => Unit): Completed = new Completed {
    def process(): Unit = p
  }
  def noTriggers[Task[_]] = new Triggers(Map.empty, Map.empty, idFun)

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
  def apply[A](a: Task[A]): Node[A]
  def inline1[A](a: Task[A]): Option[() => A]
}

final class Triggers(
    val runBefore: collection.Map[Task[?], Seq[Task[?]]],
    val injectFor: collection.Map[Task[?], Seq[Task[?]]],
    val onComplete: RMap[Task, Result] => RMap[Task, Result],
)

private[sbt] final class Execute(
    config: Execute.Config,
    triggers: Triggers,
    progress: ExecuteProgress
)(using view: NodeView) {
  import Execute.*
  type Strategy = CompletionService[Task[?], Completed]

  private[this] val forward = taskMap[IDSet[Task[?]]]
  private[this] val reverse = taskMap[Iterable[Task[?]]]
  private[this] val callers = taskPMap[[X] =>> IDSet[Task[X]]]
  private[this] val state = taskMap[State]
  private[this] val viewCache = taskPMap[Node]
  private[this] val results = taskPMap[Result]

  private[this] val getResult: [A] => Task[A] => Result[A] = [A] =>
    (a: Task[A]) =>
      view.inline1(a) match
        case Some(v) => Result.Value(v())
        case None    => results(a)
  private[this] type State = State.Value
  private[this] object State extends Enumeration {
    val Pending, Running, Calling, Done = Value
  }
  import State.{ Pending, Running, Calling, Done }

  val init = progress.initial()

  def dump: String =
    "State: " + state.toString + "\n\nResults: " + results + "\n\nCalls: " + callers + "\n\n"

  def run[A](root: Task[A])(using strategy: Strategy): Result[A] =
    try {
      runKeep(root)(root)
    } catch { case i: Incomplete => Result.Inc(i) }

  def runKeep[A](root: Task[A])(using strategy: Strategy): RMap[Task, Result] = {
    assert(state.isEmpty, "Execute already running/ran.")

    addNew(root)
    processAll()
    assert(results contains root, "No result for root node.")
    val finalResults = triggers.onComplete(results)
    progress.afterAllCompleted(finalResults)
    progress.stop()
    finalResults
  }

  def processAll()(using strategy: Strategy): Unit = {
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

  def call[A](node: Task[A], target: Task[A])(using strategy: Strategy): Unit = {
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

  def retire[A](node: Task[A], result: Result[A])(using strategy: Strategy): Unit = {
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
  def callerResult[A](node: Task[A], result: Result[A]): Result[A] =
    result match {
      case _: Result.Value[A] => result
      case Result.Inc(i)      => Result.Inc(Incomplete(Some(node), tpe = i.tpe, causes = i :: Nil))
    }

  def notifyDone(node: Task[?], dependent: Task[?])(using strategy: Strategy): Unit = {
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
  def addChecked[A](node: Task[A])(using strategy: Strategy): Unit = {
    if (!added(node)) addNew(node)

    post { addedInv(node) }
  }

  /**
   * Adds a node that has not yet been registered with the system. If all of the node's dependencies
   * have finished, the node's computation is scheduled to run. The node's dependencies will be
   * added (transitively) if they are not already registered.
   */
  def addNew(node: Task[?])(using strategy: Strategy): Unit = {
    pre { newPre(node) }

    val v = register(node)
    val deps = dependencies(v) ++ runBefore(node)
    val active = IDSet[Task[?]](deps filter notDone)
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
  def ready(node: Task[?])(using strategy: Strategy): Unit = {
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
  def register[A](node: Task[A]): Node[A] = {
    state(node) = Pending
    reverse(node) = Seq()
    viewCache.getOrUpdate(node, view(node))
  }

  /** Send the work for this node to the provided Strategy. */
  def submit(node: Task[?])(using strategy: Strategy): Unit = {
    val v = viewCache(node)
    val rs = v.alist.transform(v.in)(getResult)
    // v.alist.transform(v.in)(getResult)
    strategy.submit(node, () => work(node, v.work(rs)))
  }

  /**
   * Evaluates the computation 'f' for 'node'. This returns a Completed instance, which contains the
   * post-processing to perform after the result is retrieved from the Strategy.
   */
  def work[A](node: Task[A], f: => Either[Task[A], A])(using strategy: Strategy): Completed = {
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
  private[this] def rewrap[A](
      rawResult: Either[Incomplete, Either[Task[A], A]]
  ): Either[Task[A], Result[A]] =
    rawResult match {
      case Left(i)             => Right(Result.Inc(i))
      case Right(Right(v))     => Right(Result.Value(v))
      case Right(Left(target)) => Left(target)
    }

  def remove[K, V](map: Map[K, V], k: K): V =
    map.remove(k).getOrElse(sys.error("Key '" + k + "' not in map :\n" + map))

  def addReverse(node: Task[?], dependent: Task[?]): Unit =
    reverse(node) ++= Seq(dependent)
  def addCaller[A](caller: Task[A], target: Task[A]): Unit =
    callers.getOrUpdate(target, IDSet.create) += caller

  def dependencies(node: Task[?]): Iterable[Task[?]] = dependencies(viewCache(node))
  def dependencies(v: Node[?]): Iterable[Task[?]] =
    v.alist.toList(v.in).filter(dep => view.inline1(dep).isEmpty)

  def runBefore(node: Task[?]): Seq[Task[?]] = triggers.runBefore.getOrElse(node, nilSeq)
  def triggeredBy(node: Task[?]): Seq[Task[?]] = triggers.injectFor.getOrElse(node, nilSeq)

  // Contracts

  def addedInv(node: Task[?]): Unit = topologicalSort(node).foreach(addedCheck)
  def addedCheck(node: Task[?]): Unit = {
    assert(added(node), "Not added: " + node)
    assert(viewCache.contains(node), "Not in view cache: " + node)
    dependencyCheck(node)
  }
  def dependencyCheck(node: Task[?]): Unit = {
    dependencies(node) foreach { dep =>
      def onOpt[A](o: Option[A])(f: A => Boolean) = o match {
        case None => false; case Some(x) => f(x)
      }
      def checkForward = onOpt(forward.get(node))(_.contains(dep))
      def checkReverse = onOpt(reverse.get(dep))(_.exists(_ == node))
      assert(done(dep) ^ (checkForward && checkReverse))
    }
  }
  def pendingInv(node: Task[?]): Unit = {
    assert(atState(node, Pending))
    assert((dependencies(node) ++ runBefore(node)).exists(notDone))
  }
  def runningInv(node: Task[?]): Unit = {
    assert(dependencies(node).forall(done))
    assert(!(forward.contains(node)))
  }
  def newPre(node: Task[?]): Unit = {
    isNew(node)
    assert(!(reverse.contains(node)))
    assert(!(forward.contains(node)))
    assert(!(callers.contains(node)))
    assert(!(viewCache.contains(node)))
    assert(!(results.contains(node)))
  }

  def topologicalSort(node: Task[?]): Seq[Task[?]] = {
    val seen = IDSet.create[Task[?]]
    def visit(n: Task[?]): List[Task[?]] =
      seen.process(n)(List.empty) {
        val deps: List[Task[?]] =
          dependencies(n).foldLeft(List.empty)((ss, dep) => visit(dep) ::: ss)
        node :: deps
      }

    visit(node).reverse
  }

  def readyInv(node: Task[?]): Unit = {
    assert(dependencies(node).forall(done))
    assert(!(forward.contains(node)))
  }

  // cyclic reference checking

  def snapshotCycleCheck(): Unit =
    callers.toSeq foreach { case (called, callers) =>
      for (caller <- callers) cycleCheck(caller, called)
    }

  def cycleCheck(node: Task[?], target: Task[?]): Unit = {
    if (node eq target) cyclic(node, target, "Cannot call self")
    val all = IDSet.create[Task[?]]
    def allCallers(n: Task[?]): Unit = (all process n)(()) {
      callers.get(n).toList.flatten.foreach(allCallers)
    }
    allCallers(node)
    if (all contains target) cyclic(node, target, "Cyclic reference")
  }
  def cyclic(caller: Task[?], target: Task[?], msg: String) =
    throw new Incomplete(
      Some(caller),
      message = Some(msg),
      directCause = Some(new CyclicException(caller, target, msg))
    )
  final class CyclicException(val caller: Task[?], val target: Task[?], msg: String)
      extends Exception(msg)

  // state testing

  def pending(d: Task[?]) = atState(d, Pending)
  def running(d: Task[?]) = atState(d, Running)
  def calling(d: Task[?]) = atState(d, Calling)
  def done(d: Task[?]) = atState(d, Done)
  def notDone(d: Task[?]) = !done(d)
  private def atState(d: Task[?], s: State) = state.get(d) == Some(s)
  def isNew(d: Task[?]) = !added(d)
  def added(d: Task[?]) = state.contains(d)
  def complete = state.values.forall(_ == Done)

  def pre(f: => Unit) = if (checkPreAndPostConditions) f
  def post(f: => Unit) = if (checkPreAndPostConditions) f
}
