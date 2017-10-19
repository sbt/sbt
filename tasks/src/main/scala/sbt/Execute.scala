/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.internal.util.ErrorHandling.wideConvert
import sbt.internal.util.{ DelegatingPMap, PMap, RMap, IDSet, ~> }
import sbt.internal.util.Types._
import Execute._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.JavaConverters._
import mutable.Map

private[sbt] object Execute {
  def idMap[A, B]: Map[A, B] = (new java.util.IdentityHashMap[A, B]).asScala
  def pMap[A[_], B[_]]: PMap[A, B] = new DelegatingPMap[A, B](idMap)
  private[sbt] def completed(p: => Unit): Completed = new Completed {
    def process(): Unit = p
  }
  def noTriggers[A[_]] = new Triggers[A](Map.empty, Map.empty, idFun)

  def config(checkCycles: Boolean, overwriteNode: Incomplete => Boolean = const(false)): Config =
    new Config(checkCycles, overwriteNode)
  final class Config private[sbt] (val checkCycles: Boolean,
                                   val overwriteNode: Incomplete => Boolean)

  final val checkPreAndPostConditions =
    sys.props.get("sbt.execute.extrachecks").exists(java.lang.Boolean.parseBoolean)
}
sealed trait Completed {
  def process(): Unit
}
private[sbt] trait NodeView[A[_]] {
  def apply[T](a: A[T]): Node[A, T]
  def inline[T](a: A[T]): Option[() => T]
}
final class Triggers[A[_]](val runBefore: collection.Map[A[_], Seq[A[_]]],
                           val injectFor: collection.Map[A[_], Seq[A[_]]],
                           val onComplete: RMap[A, Result] => RMap[A, Result])

private[sbt] final class Execute[A[_] <: AnyRef](
    config: Config,
    triggers: Triggers[A],
    progress: ExecuteProgress[A])(implicit view: NodeView[A]) {
  type Strategy = CompletionService[A[_], Completed]

  private[this] val forward = idMap[A[_], IDSet[A[_]]]
  private[this] val reverse = idMap[A[_], Iterable[A[_]]]
  private[this] val callers = pMap[A, Compose[IDSet, A]#Apply]
  private[this] val state = idMap[A[_], State]
  private[this] val viewCache = pMap[A, Node[A, ?]]
  private[this] val results = pMap[A, Result]

  private[this] val getResult: A ~> Result = λ[A ~> Result](
    a =>
      view.inline(a) match {
        case Some(v) => Value(v())
        case None    => results(a)
    }
  )
  private[this] var progressState: progress.S = progress.initial

  private[this] type State = State.Value
  private[this] object State extends Enumeration {
    val Pending, Running, Calling, Done = Value
  }
  import State.{ Pending, Running, Calling, Done }

  def dump: String =
    "State: " + state.toString + "\n\nResults: " + results + "\n\nCalls: " + callers + "\n\n"

  def run[T](root: A[T])(implicit strategy: Strategy): Result[T] =
    try { runKeep(root)(strategy)(root) } catch { case i: Incomplete => Inc(i) }
  def runKeep[T](root: A[T])(implicit strategy: Strategy): RMap[A, Result] = {
    assert(state.isEmpty, "Execute already running/ran.")

    addNew(root)
    processAll()
    assert(results contains root, "No result for root node.")
    val finalResults = triggers.onComplete(results)
    progressState = progress.allCompleted(progressState, finalResults)
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

      (strategy.take()).process()
      if (reverse.nonEmpty) next()
    }
    next()

    post {
      assert(reverse.isEmpty, "Did not process everything.")
      assert(complete, "Not all state was Done.")
    }
  }
  def dumpCalling: String = state.filter(_._2 == Calling).mkString("\n\t")

  def call[T](node: A[T], target: A[T])(implicit strategy: Strategy): Unit = {
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

  def retire[T](node: A[T], result: Result[T])(implicit strategy: Strategy): Unit = {
    pre {
      assert(running(node) | calling(node))
      readyInv(node)
    }

    results(node) = result
    state(node) = Done
    progressState = progress.completed(progressState, node, result)
    remove(reverse, node) foreach { dep =>
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
  def callerResult[T](node: A[T], result: Result[T]): Result[T] =
    result match {
      case _: Value[T] => result
      case Inc(i)      => Inc(Incomplete(Some(node), tpe = i.tpe, causes = i :: Nil))
    }

  def notifyDone(node: A[_], dependent: A[_])(implicit strategy: Strategy): Unit = {
    val f = forward(dependent)
    f -= node
    if (f.isEmpty) {
      remove(forward, dependent)
      ready(dependent)
    }
  }

  /**
   * Ensures the given node has been added to the system.
   * Once added, a node is pending until its inputs and dependencies have completed.
   * Its computation is then evaluated and made available for nodes that have it as an input.
   */
  def addChecked[T](node: A[T])(implicit strategy: Strategy): Unit = {
    if (!added(node)) addNew(node)

    post { addedInv(node) }
  }

  /**
   * Adds a node that has not yet been registered with the system.
   * If all of the node's dependencies have finished, the node's computation is scheduled to run.
   * The node's dependencies will be added (transitively) if they are not already registered.
   */
  def addNew[T](node: A[T])(implicit strategy: Strategy): Unit = {
    pre { newPre(node) }

    val v = register(node)
    val deps = dependencies(v) ++ runBefore(node)
    val active = IDSet[A[_]](deps filter notDone)
    progressState = progress.registered(progressState,
                                        node,
                                        deps,
                                        active.toList
                                        /** active is mutable, so take a snapshot */
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
  def ready[T](node: A[T])(implicit strategy: Strategy): Unit = {
    pre {
      assert(pending(node))
      readyInv(node)
      assert(reverse contains node)
    }

    state(node) = Running
    progressState = progress.ready(progressState, node)
    submit(node)

    post {
      readyInv(node)
      assert(reverse contains node)
      assert(running(node))
    }
  }

  /** Enters the given node into the system. */
  def register[T](node: A[T]): Node[A, T] = {
    state(node) = Pending
    reverse(node) = Seq()
    viewCache.getOrUpdate(node, view(node))
  }

  /** Send the work for this node to the provided Strategy. */
  def submit[T](node: A[T])(implicit strategy: Strategy): Unit = {
    val v = viewCache(node)
    val rs = v.alist.transform(v.in, getResult)
    strategy.submit(node, () => work(node, v.work(rs)))
  }

  /**
   * Evaluates the computation 'f' for 'node'.
   * This returns a Completed instance, which contains the post-processing to perform after the result is retrieved from the Strategy.
   */
  def work[T](node: A[T], f: => Either[A[T], T])(implicit strategy: Strategy): Completed = {
    progress.workStarting(node)
    val rawResult = wideConvert(f).left.map {
      case i: Incomplete => if (config.overwriteNode(i)) i.copy(node = Some(node)) else i
      case e             => Incomplete(Some(node), Incomplete.Error, directCause = Some(e))
    }
    val result = rewrap(rawResult)
    progress.workFinished(node, result)
    completed {
      result match {
        case Right(v)     => retire(node, v)
        case Left(target) => call(node, target)
      }
    }
  }
  private[this] def rewrap[T](
      rawResult: Either[Incomplete, Either[A[T], T]]): Either[A[T], Result[T]] =
    rawResult match {
      case Left(i)             => Right(Inc(i))
      case Right(Right(v))     => Right(Value(v))
      case Right(Left(target)) => Left(target)
    }

  def remove[K, V](map: Map[K, V], k: K): V =
    map.remove(k).getOrElse(sys.error("Key '" + k + "' not in map :\n" + map))

  def addReverse(node: A[_], dependent: A[_]): Unit = reverse(node) ++= Seq(dependent)
  def addCaller[T](caller: A[T], target: A[T]): Unit =
    callers.getOrUpdate(target, IDSet.create[A[T]]) += caller

  def dependencies(node: A[_]): Iterable[A[_]] = dependencies(viewCache(node))
  def dependencies(v: Node[A, _]): Iterable[A[_]] =
    v.alist.toList(v.in).filter(dep => view.inline(dep).isEmpty)

  def runBefore(node: A[_]): Seq[A[_]] = getSeq(triggers.runBefore, node)
  def triggeredBy(node: A[_]): Seq[A[_]] = getSeq(triggers.injectFor, node)
  def getSeq(map: collection.Map[A[_], Seq[A[_]]], node: A[_]): Seq[A[_]] =
    map.getOrElse(node, Nil)

  // Contracts

  def addedInv(node: A[_]): Unit = topologicalSort(node) foreach addedCheck
  def addedCheck(node: A[_]): Unit = {
    assert(added(node), "Not added: " + node)
    assert(viewCache contains node, "Not in view cache: " + node)
    dependencyCheck(node)
  }
  def dependencyCheck(node: A[_]): Unit = {
    dependencies(node) foreach { dep =>
      def onOpt[T](o: Option[T])(f: T => Boolean) = o match {
        case None => false; case Some(x) => f(x)
      }
      def checkForward = onOpt(forward.get(node)) { _ contains dep }
      def checkReverse = onOpt(reverse.get(dep)) { _.exists(_ == node) }
      assert(done(dep) ^ (checkForward && checkReverse))
    }
  }
  def pendingInv(node: A[_]): Unit = {
    assert(atState(node, Pending))
    assert((dependencies(node) ++ runBefore(node)) exists notDone)
  }
  def runningInv(node: A[_]): Unit = {
    assert(dependencies(node) forall done)
    assert(!(forward contains node))
  }
  def newPre(node: A[_]): Unit = {
    isNew(node)
    assert(!(reverse contains node))
    assert(!(forward contains node))
    assert(!(callers contains node))
    assert(!(viewCache contains node))
    assert(!(results contains node))
  }

  def topologicalSort(node: A[_]): Seq[A[_]] = {
    val seen = IDSet.create[A[_]]
    def visit(n: A[_]): List[A[_]] =
      (seen process n)(List[A[_]]()) {
        node :: (List[A[_]]() /: dependencies(n)) { (ss, dep) =>
          visit(dep) ::: ss
        }
      }

    visit(node).reverse
  }

  def readyInv(node: A[_]): Unit = {
    assert(dependencies(node) forall done)
    assert(!(forward contains node))
  }

  // cyclic reference checking

  def snapshotCycleCheck(): Unit =
    for ((called: A[c], callers) <- callers.toSeq; caller <- callers)
      cycleCheck(caller.asInstanceOf[A[c]], called)

  def cycleCheck[T](node: A[T], target: A[T]): Unit = {
    if (node eq target) cyclic(node, target, "Cannot call self")
    val all = IDSet.create[A[T]]
    def allCallers(n: A[T]): Unit = (all process n)(()) {
      callers.get(n).toList.flatten.foreach(allCallers)
    }
    allCallers(node)
    if (all contains target) cyclic(node, target, "Cyclic reference")
  }
  def cyclic[T](caller: A[T], target: A[T], msg: String) =
    throw new Incomplete(Some(caller),
                         message = Some(msg),
                         directCause = Some(new CyclicException(caller, target, msg)))
  final class CyclicException[T](val caller: A[T], val target: A[T], msg: String)
      extends Exception(msg)

  // state testing

  def pending(d: A[_]) = atState(d, Pending)
  def running(d: A[_]) = atState(d, Running)
  def calling(d: A[_]) = atState(d, Calling)
  def done(d: A[_]) = atState(d, Done)
  def notDone(d: A[_]) = !done(d)
  def atState(d: A[_], s: State) = state.get(d) == Some(s)
  def isNew(d: A[_]) = !added(d)
  def added(d: A[_]) = state contains d
  def complete = state.values.forall(_ == Done)

  def pre(f: => Unit) = if (checkPreAndPostConditions) f
  def post(f: => Unit) = if (checkPreAndPostConditions) f
}
