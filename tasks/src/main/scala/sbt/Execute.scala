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
  def inline[A](a: F[A]): Option[() => A]
}
final class Triggers[F[_]](
    val runBefore: collection.Map[F[_], Seq[F[_]]],
    val injectFor: collection.Map[F[_], Seq[F[_]]],
    val onComplete: RMap[F, Result] => RMap[F, Result]
)

private[sbt] final class Execute[F[_] <: AnyRef](
    config: Config,
    triggers: Triggers[F],
    progress: ExecuteProgress[F]
)(implicit view: NodeView[F]) {
  type Strategy = CompletionService[F[_], Completed]

  private[this] val forward = idMap[F[_], IDSet[F[_]]]
  private[this] val reverse = idMap[F[_], Iterable[F[_]]]
  private[this] val callers = pMap[F, Compose[IDSet, F]#Apply]
  private[this] val state = idMap[F[_], State]
  private[this] val viewCache = pMap[F, Node[F, *]]
  private[this] val results = pMap[F, Result]

  private[this] val getResult: F ~> Result = λ[F ~> Result](
    a =>
      view.inline(a) match {
        case Some(v) => Value(v())
        case None    => results(a)
      }
  )
  private[this] type State = State.Value
  private[this] object State extends Enumeration {
    val Pending, Running, Calling, Done = Value
  }
  import State.{ Pending, Running, Calling, Done }

  val init = progress.initial()

  def dump: String =
    "State: " + state.toString + "\n\nResults: " + results + "\n\nCalls: " + callers + "\n\n"

  def run[A](root: F[A])(implicit strategy: Strategy): Result[A] =
    try {
      runKeep(root)(strategy)(root)
    } catch { case i: Incomplete => Inc(i) }

  def runKeep[A](root: F[A])(implicit strategy: Strategy): RMap[F, Result] = {
    assert(state.isEmpty, "Execute already running/ran.")

    addNew(root)
    processAll()
    assert(results contains root, "No result for root node.")
    val finalResults = triggers.onComplete(results)
    progress.afterAllCompleted(finalResults)
    progress.stop()
    finalResults
  }

  def processAll()(implicit strategy: Strategy): Unit = {
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

  def call[A](node: F[A], target: F[A])(implicit strategy: Strategy): Unit = {
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
      if (done(target))
        assert(done(node))
      else {
        assert(calling(node))
        assert(callers(target) contains node)
      }
      readyInv(node)
    }
  }

  def retire[A](node: F[A], result: Result[A])(implicit strategy: Strategy): Unit = {
    pre {
      assert(running(node) | calling(node))
      readyInv(node)
    }

    results(node) = result
    state(node) = Done
    progress.afterCompleted(node, result)
    remove(reverse.asInstanceOf[Map[F[A], Iterable[F[_]]]], node) foreach { dep =>
      notifyDone(node, dep)
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
      assert(!(reverse contains node))
      assert(!(callers contains node))
      assert(triggeredBy(node) forall added)
    }
  }
  def callerResult[A](node: F[A], result: Result[A]): Result[A] =
    result match {
      case _: Value[A] => result
      case Inc(i)      => Inc(Incomplete(Some(node), tpe = i.tpe, causes = i :: Nil))
    }

  def notifyDone(node: F[_], dependent: F[_])(implicit strategy: Strategy): Unit = {
    val f = forward(dependent)
    f -= node
    if (f.isEmpty) {
      remove[F[_], IDSet[F[_]]](forward, dependent)
      ready(dependent)
    }
  }

  /**
   * Ensures the given node has been added to the system.
   * Once added, a node is pending until its inputs and dependencies have completed.
   * Its computation is then evaluated and made available for nodes that have it as an input.
   */
  def addChecked[A](node: F[A])(implicit strategy: Strategy): Unit = {
    if (!added(node)) addNew(node)

    post { addedInv(node) }
  }

  /**
   * Adds a node that has not yet been registered with the system.
   * If all of the node's dependencies have finished, the node's computation is scheduled to run.
   * The node's dependencies will be added (transitively) if they are not already registered.
   */
  def addNew[A](node: F[A])(implicit strategy: Strategy): Unit = {
    pre { newPre(node) }

    val v = register(node)
    val deps = dependencies(v) ++ runBefore(node)
    val active = IDSet[F[_]](deps filter notDone)
    progress.afterRegistered(
      node,
      deps,
      active.toList
      /* active is mutable, so take a snapshot */
    )

    if (active.isEmpty)
      ready(node)
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

  /** Called when a pending 'node' becomes runnable.  All of its dependencies must be done.  This schedules the node's computation with 'strategy'.*/
  def ready[A](node: F[A])(implicit strategy: Strategy): Unit = {
    pre {
      assert(pending(node))
      readyInv(node)
      assert(reverse contains node)
    }

    state(node) = Running
    progress.afterReady(node)
    submit(node)

    post {
      readyInv(node)
      assert(reverse contains node)
      assert(running(node))
    }
  }

  /** Enters the given node into the system. */
  def register[A](node: F[A]): Node[F, A] = {
    state(node) = Pending
    reverse(node) = Seq()
    viewCache.getOrUpdate(node, view(node))
  }

  /** Send the work for this node to the provided Strategy. */
  def submit[A](node: F[A])(implicit strategy: Strategy): Unit = {
    val v = viewCache(node)
    val rs = v.alist.transform(v.in, getResult)
    strategy.submit(node, () => work(node, v.work(rs)))
  }

  /**
   * Evaluates the computation 'f' for 'node'.
   * This returns a Completed instance, which contains the post-processing to perform after the result is retrieved from the Strategy.
   */
  def work[A](node: F[A], f: => Either[F[A], A])(implicit strategy: Strategy): Completed = {
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
      rawResult: Either[Incomplete, Either[F[A], A]]
  ): Either[F[A], Result[A]] =
    rawResult match {
      case Left(i)             => Right(Inc(i))
      case Right(Right(v))     => Right(Value(v))
      case Right(Left(target)) => Left(target)
    }

  def remove[K, V](map: Map[K, V], k: K): V =
    map.remove(k).getOrElse(sys.error("Key '" + k + "' not in map :\n" + map))

  def addReverse(node: F[_], dependent: F[_]): Unit = reverse(node) ++= Seq(dependent)
  def addCaller[A](caller: F[A], target: F[A]): Unit =
    callers.getOrUpdate(target, IDSet.create[F[A]]) += caller

  def dependencies(node: F[_]): Iterable[F[_]] = dependencies(viewCache(node))
  def dependencies(v: Node[F, _]): Iterable[F[_]] =
    v.alist.toList(v.in).filter(dep => view.inline(dep).isEmpty)

  def runBefore(node: F[_]): Seq[F[_]] = getSeq(triggers.runBefore, node)
  def triggeredBy(node: F[_]): Seq[F[_]] = getSeq(triggers.injectFor, node)
  def getSeq(map: collection.Map[F[_], Seq[F[_]]], node: F[_]): Seq[F[_]] =
    map.getOrElse(node, nilSeq[F[_]])

  // Contracts

  def addedInv(node: F[_]): Unit = topologicalSort(node) foreach addedCheck
  def addedCheck(node: F[_]): Unit = {
    assert(added(node), "Not added: " + node)
    assert(viewCache contains node, "Not in view cache: " + node)
    dependencyCheck(node)
  }
  def dependencyCheck(node: F[_]): Unit = {
    dependencies(node) foreach { dep =>
      def onOpt[A](o: Option[A])(f: A => Boolean) = o match {
        case None => false; case Some(x) => f(x)
      }
      def checkForward = onOpt(forward.get(node)) { _ contains dep }
      def checkReverse = onOpt(reverse.get(dep)) { _.exists(_ == node) }
      assert(done(dep) ^ (checkForward && checkReverse))
    }
  }
  def pendingInv(node: F[_]): Unit = {
    assert(atState(node, Pending))
    assert((dependencies(node) ++ runBefore(node)) exists notDone)
  }
  def runningInv(node: F[_]): Unit = {
    assert(dependencies(node) forall done)
    assert(!(forward contains node))
  }
  def newPre(node: F[_]): Unit = {
    isNew(node)
    assert(!(reverse contains node))
    assert(!(forward contains node))
    assert(!(callers contains node))
    assert(!(viewCache contains node))
    assert(!(results contains node))
  }

  def topologicalSort(node: F[_]): Seq[F[_]] = {
    val seen = IDSet.create[F[_]]
    def visit(n: F[_]): List[F[_]] =
      (seen process n)(List[F[_]]()) {
        node :: dependencies(n).foldLeft(List[F[_]]()) { (ss, dep) =>
          visit(dep) ::: ss
        }
      }

    visit(node).reverse
  }

  def readyInv(node: F[_]): Unit = {
    assert(dependencies(node) forall done)
    assert(!(forward contains node))
  }

  // cyclic reference checking

  def snapshotCycleCheck(): Unit =
    callers.toSeq foreach {
      case (called: F[c], callers) =>
        for (caller <- callers) cycleCheck(caller.asInstanceOf[F[c]], called)
      case _ => ()
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

  def pending(d: F[_]) = atState(d, Pending)
  def running(d: F[_]) = atState(d, Running)
  def calling(d: F[_]) = atState(d, Calling)
  def done(d: F[_]) = atState(d, Done)
  def notDone(d: F[_]) = !done(d)
  def atState(d: F[_], s: State) = state.get(d) == Some(s)
  def isNew(d: F[_]) = !added(d)
  def added(d: F[_]) = state contains d
  def complete = state.values.forall(_ == Done)

  def pre(f: => Unit) = if (checkPreAndPostConditions) f
  def post(f: => Unit) = if (checkPreAndPostConditions) f
}
