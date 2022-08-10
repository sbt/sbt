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
import sbt.internal.util.Types._
import sbt.internal.util.Util.nilSeq
import Execute._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.JavaConverters._
import mutable.Map
import sbt.internal.util.AList

private[sbt] object Execute {
  def idMap[A1, A2]: Map[A1, A2] = (new java.util.IdentityHashMap[A1, A2]).asScala
  def pMap[F1[_], F2[_]]: PMap[F1, F2] = new DelegatingPMap[F1, F2](idMap)
  private[sbt] def completed(p: => Unit): Completed = new Completed {
    def process(): Unit = p
  }
  def noTriggers[F[_]] = new Triggers[F](Map.empty, Map.empty, idFun)

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

private[sbt] trait NodeView[F[_]] {
  def apply[A](a: F[A]): Node[F, A]
  def inline1[A](a: F[A]): Option[() => A]
}

final class Triggers[F[_]](
    val runBefore: collection.Map[F[Any], Seq[F[Any]]],
    val injectFor: collection.Map[F[Any], Seq[F[Any]]],
    val onComplete: RMap[F, Result] => RMap[F, Result],
)

private[sbt] final class Execute[F[_] <: AnyRef](
    config: Config,
    triggers: Triggers[F],
    progress: ExecuteProgress[F]
)(using view: NodeView[F]) {
  type Strategy = CompletionService[F[Any], Completed]

  private[this] val forward = idMap[F[Any], IDSet[F[Any]]]
  private[this] val reverse = idMap[F[Any], Iterable[F[Any]]]
  private[this] val callers = pMap[F, Compose[IDSet, F]]
  private[this] val state = idMap[F[Any], State]
  private[this] val viewCache = pMap[F, Node[F, *]]
  private[this] val results = pMap[F, Result]

  private[this] val getResult: [A] => F[A] => Result[A] = [A] =>
    (a: F[A]) =>
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

  def run[A](root: F[A])(using strategy: Strategy): Result[A] =
    try {
      runKeep(root)(root)
    } catch { case i: Incomplete => Result.Inc(i) }

  def runKeep[A](root: F[A])(using strategy: Strategy): RMap[F, Result] = {
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

  def call[A](node: F[A], target: F[A])(using strategy: Strategy): Unit = {
    if (config.checkCycles) cycleCheck(node, target)
    pre {
      assert(running(node))
      readyInv(node)
    }

    results.get(target) match {
      case Some(result) => retire(node, result)
      case None =>
        state(node.asInstanceOf) = Calling
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

  def retire[A](node: F[A], result: Result[A])(using strategy: Strategy): Unit = {
    pre {
      assert(running(node) | calling(node))
      readyInv(node)
    }

    results(node) = result
    state(node.asInstanceOf) = Done
    progress.afterCompleted(node, result)
    remove(reverse.asInstanceOf[Map[F[A], Iterable[F[Any]]]], node) foreach { dep =>
      notifyDone(node, dep.asInstanceOf)
    }
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
      assert(!(reverse.contains(node.asInstanceOf)))
      assert(!(callers.contains(node)))
      assert(triggeredBy(node) forall added)
    }
  }
  def callerResult[A](node: F[A], result: Result[A]): Result[A] =
    result match {
      case _: Result.Value[A] => result
      case Result.Inc(i)      => Result.Inc(Incomplete(Some(node), tpe = i.tpe, causes = i :: Nil))
    }

  def notifyDone[A](node: F[A], dependent: F[Any])(using strategy: Strategy): Unit = {
    val f = forward(dependent)
    f -= node.asInstanceOf
    if (f.isEmpty) {
      remove[F[Any], IDSet[F[Any]]](forward.asInstanceOf, dependent)
      ready[Any](dependent)
    }
  }

  /**
   * Ensures the given node has been added to the system. Once added, a node is pending until its
   * inputs and dependencies have completed. Its computation is then evaluated and made available
   * for nodes that have it as an input.
   */
  def addChecked[A](node: F[A])(using strategy: Strategy): Unit = {
    if (!added(node)) addNew(node)

    post { addedInv(node) }
  }

  /**
   * Adds a node that has not yet been registered with the system. If all of the node's dependencies
   * have finished, the node's computation is scheduled to run. The node's dependencies will be
   * added (transitively) if they are not already registered.
   */
  def addNew[A](node: F[A])(using strategy: Strategy): Unit = {
    pre { newPre(node) }

    val v = register(node)
    val deps: Iterable[F[Any]] = dependencies(v) ++ runBefore(node.asInstanceOf)
    val active = IDSet[F[Any]](deps filter notDone.asInstanceOf)
    progress.afterRegistered(
      node.asInstanceOf,
      deps,
      active.toList
      /* active is mutable, so take a snapshot */
    )

    if (active.isEmpty) ready(node)
    else {
      forward(node.asInstanceOf) = active.asInstanceOf
      for (a <- active) {
        addChecked[Any](a.asInstanceOf)
        addReverse[Any](a.asInstanceOf, node.asInstanceOf)
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
  def ready[A](node: F[A])(using strategy: Strategy): Unit = {
    pre {
      assert(pending(node))
      readyInv(node)
      assert(reverse.contains(node.asInstanceOf))
    }

    state(node.asInstanceOf) = Running
    progress.afterReady(node.asInstanceOf)
    submit(node)

    post {
      readyInv(node)
      assert(reverse.contains(node.asInstanceOf))
      assert(running(node))
    }
  }

  /** Enters the given node into the system. */
  def register[A](node: F[A]): Node[F, A] = {
    state(node.asInstanceOf) = Pending
    reverse(node.asInstanceOf) = Seq()
    viewCache.getOrUpdate(node, view(node))
  }

  /** Send the work for this node to the provided Strategy. */
  def submit[A](node: F[A])(using strategy: Strategy): Unit = {
    val v = viewCache(node)
    val rs = v.alist.transform[F, Result](v.in)(getResult)
    // v.alist.transform(v.in)(getResult)
    strategy.submit(node.asInstanceOf, () => work(node, v.work(rs)))
  }

  /**
   * Evaluates the computation 'f' for 'node'. This returns a Completed instance, which contains the
   * post-processing to perform after the result is retrieved from the Strategy.
   */
  def work[A](node: F[A], f: => Either[F[A], A])(using strategy: Strategy): Completed = {
    progress.beforeWork(node.asInstanceOf)
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
      rawResult: Either[Incomplete, Either[F[A], A]]
  ): Either[F[A], Result[A]] =
    rawResult match {
      case Left(i)             => Right(Result.Inc(i))
      case Right(Right(v))     => Right(Result.Value(v))
      case Right(Left(target)) => Left(target)
    }

  def remove[K, V](map: Map[K, V], k: K): V =
    map.remove(k).getOrElse(sys.error("Key '" + k + "' not in map :\n" + map))

  def addReverse[A](node: F[A], dependent: F[Any]): Unit =
    reverse(node.asInstanceOf) ++= Seq(dependent)
  def addCaller[A](caller: F[A], target: F[A]): Unit =
    callers.getOrUpdate(target, IDSet.create[F[A]]) += caller

  def dependencies[A](node: F[A]): Iterable[F[Any]] = dependencies(viewCache(node.asInstanceOf))
  def dependencies[A](v: Node[F, A]): Iterable[F[Any]] =
    v.alist.toList[F](v.in).filter(dep => view.inline1(dep).isEmpty)

  def runBefore[A](node: F[A]): Seq[F[A]] =
    getSeq[A](triggers.runBefore, node)
  def triggeredBy[A](node: F[A]): Seq[F[A]] = getSeq(triggers.injectFor, node)
  def getSeq[A](map: collection.Map[F[Any], Seq[F[Any]]], node: F[A]): Seq[F[A]] =
    map.getOrElse(node.asInstanceOf, nilSeq[F[Any]]).asInstanceOf

  // Contracts

  def addedInv[A](node: F[A]): Unit = topologicalSort(node) foreach addedCheck
  def addedCheck[A](node: F[A]): Unit = {
    assert(added(node), "Not added: " + node)
    assert(viewCache.contains[Any](node.asInstanceOf), "Not in view cache: " + node)
    dependencyCheck(node.asInstanceOf)
  }
  def dependencyCheck(node: F[Any]): Unit = {
    dependencies(node) foreach { dep =>
      def onOpt[A](o: Option[A])(f: A => Boolean) = o match {
        case None => false; case Some(x) => f(x)
      }
      def checkForward = onOpt(forward.get(node.asInstanceOf)) { _ contains dep.asInstanceOf }
      def checkReverse = onOpt(reverse.get(dep.asInstanceOf)) { _.exists(_ == node) }
      assert(done(dep.asInstanceOf) ^ (checkForward && checkReverse))
    }
  }
  def pendingInv[A](node: F[A]): Unit = {
    assert(atState(node, Pending))
    assert((dependencies(node) ++ runBefore(node)) exists notDone.asInstanceOf)
  }
  def runningInv[A](node: F[A]): Unit = {
    assert(dependencies(node) forall done.asInstanceOf)
    assert(!(forward.contains(node.asInstanceOf)))
  }
  def newPre[A](node: F[A]): Unit = {
    isNew(node)
    assert(!(reverse.contains(node.asInstanceOf)))
    assert(!(forward.contains(node.asInstanceOf)))
    assert(!(callers.contains[Any](node.asInstanceOf)))
    assert(!(viewCache.contains[Any](node.asInstanceOf)))
    assert(!(results.contains[Any](node.asInstanceOf)))
  }

  def topologicalSort[A](node: F[A]): Seq[F[Any]] = {
    val seen = IDSet.create[F[Any]]
    def visit(n: F[Any]): List[F[Any]] =
      (seen process n)(List[F[Any]]()) {
        node.asInstanceOf :: dependencies(n).foldLeft(List[F[Any]]()) { (ss, dep) =>
          visit(dep.asInstanceOf) ::: ss
        }
      }

    visit(node.asInstanceOf).reverse
  }

  def readyInv[A](node: F[A]): Unit = {
    assert(dependencies(node) forall done.asInstanceOf)
    assert(!(forward.contains(node.asInstanceOf)))
  }

  // cyclic reference checking

  def snapshotCycleCheck(): Unit =
    callers.toSeq foreach { case (called: F[c], callers) =>
      for (caller <- callers) cycleCheck(caller.asInstanceOf[F[c]], called)
    }

  def cycleCheck[A](node: F[A], target: F[A]): Unit = {
    if (node eq target) cyclic(node, target, "Cannot call self")
    val all = IDSet.create[F[A]]
    def allCallers(n: F[A]): Unit = (all process n)(()) {
      callers.get(n).toList.flatten.foreach(allCallers)
    }
    allCallers(node)
    if (all contains target) cyclic(node, target, "Cyclic reference")
  }
  def cyclic[A](caller: F[A], target: F[A], msg: String) =
    throw new Incomplete(
      Some(caller),
      message = Some(msg),
      directCause = Some(new CyclicException(caller, target, msg))
    )
  final class CyclicException[A](val caller: F[A], val target: F[A], msg: String)
      extends Exception(msg)

  // state testing

  def pending[A](d: F[A]) = atState(d, Pending)
  def running[A](d: F[A]) = atState(d, Running)
  def calling[A](d: F[A]) = atState(d, Calling)
  def done[A](d: F[A]) = atState(d, Done)
  def notDone[A](d: F[A]) = !done(d)
  private def atState[A](d: F[A], s: State) = state.get(d.asInstanceOf) == Some(s)
  def isNew[A](d: F[A]) = !added(d)
  def added[A](d: F[A]) = state.contains(d.asInstanceOf)
  def complete = state.values.forall(_ == Done)

  def pre(f: => Unit) = if (checkPreAndPostConditions) f
  def post(f: => Unit) = if (checkPreAndPostConditions) f
}
