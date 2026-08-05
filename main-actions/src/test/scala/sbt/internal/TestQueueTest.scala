package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.Result
import java.util.concurrent.{ Callable, Executors, TimeUnit }
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

object TestQueueTest extends Properties:

  override lazy val tests: List[Test] = List(
    property("single leaser drains exactly the input", propDrainsExactly),
    property("concurrent leasers partition the input", propConcurrentPartition),
    property("poison stops serving", propPoison),
    example("empty queue leases nothing", exEmpty),
    example("frameworks are independent", exIndependentFrameworks),
    example("out of range framework leases nothing", exOutOfRange),
    example("hasWork follows both the units and the poison", exHasWork),
    example("leasers released together still never share a unit", exSimultaneousLeases),
  )

  private def genSizes: Gen[List[Int]] =
    Gen.int(Range.linear(0, 12)).list(Range.linear(1, 4))

  private def queueOf(sizes: List[Int]): (TestQueue, Vector[Vector[Int]]) =
    var next = 0
    val per = sizes.toVector.map: n =>
      val v = Vector.range(next, next + n)
      next += n
      v
    (TestQueue(per), per)

  private def drainAll(q: TestQueue, frameworks: Int): Vector[Int] =
    (0 until frameworks).toVector.flatMap: f =>
      Iterator.continually(q.lease(f)).takeWhile(_.isDefined).flatten.toVector

  def propDrainsExactly: Property =
    for sizes <- genSizes.forAll
    yield
      val (q, per) = queueOf(sizes)
      val drained = drainAll(q, per.length)
      Result
        .assert(drained.sorted == per.flatten.sorted)
        .and(Result.assert(q.remaining == 0))
        .log(s"drained=$drained expected=${per.flatten}")

  def propConcurrentPartition: Property =
    for
      sizes <- genSizes.forAll
      threads <- Gen.int(Range.linear(2, 8)).forAll
    yield
      val (q, per) = queueOf(sizes)
      val pool = Executors.newFixedThreadPool(threads)
      try
        val jobs = (1 to threads).map: _ =>
          new Callable[Vector[Int]]:
            def call(): Vector[Int] = drainAll(q, per.length)
        val results = pool.invokeAll(jobs.asJava).asScala.toVector.map(_.get)
        val all = results.flatten
        Result
          .assert(all.sorted == per.flatten.sorted)
          .and(Result.assert(all.distinct.length == all.length))
          .and(Result.assert(q.remaining == 0))
          .log(s"perThread=${results.map(_.length)} total=${all.length}")
      finally
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)
        ()

  def propPoison: Property =
    for sizes <- genSizes.filter(_.sum > 0).forAll
    yield
      val (q, per) = queueOf(sizes)
      val servedBefore = q.hasWork
      q.poison()
      val leased = drainAll(q, per.length)
      Result
        .assert(leased.isEmpty)
        .and(Result.assert(servedBefore))
        .and(Result.assert(!q.hasWork))
        .and(Result.assert(q.remaining == per.flatten.length))
        .log(s"leased=$leased remaining=${q.remaining}")

  def exEmpty: Result =
    val q = TestQueue(Vector(Vector.empty, Vector.empty))
    Result
      .assert(q.lease(0).isEmpty)
      .and(Result.assert(q.lease(1).isEmpty))
      .and(Result.assert(q.remaining == 0))
      .and(Result.assert(!q.hasWork))

  def exIndependentFrameworks: Result =
    val q = TestQueue(Vector(Vector(0, 1), Vector(2)))
    val a = q.lease(0)
    val b = q.lease(1)
    val c = q.lease(0)
    val d = q.lease(0)
    Result
      .assert(Set(a, c) == Set(Some(0), Some(1)))
      .and(Result.assert(b == Some(2)))
      .and(Result.assert(d.isEmpty))
      .and(Result.assert(q.lease(1).isEmpty))

  def exSimultaneousLeases: Result =
    // Threads running at the same time are not the same as threads colliding: the window between
    // reading the cursor and storing it back is nanoseconds wide, so a lease that lost its
    // atomicity survives `propConcurrentPartition` (whose runs lease a dozen units) most of the
    // time. What finds it is a long unsynchronised hammer on one cursor — hence the spin gate
    // rather than a barrier, since leaving a barrier is serialised by the barrier's own lock.
    val threads = math.max(4, Runtime.getRuntime().availableProcessors())
    val total = 20000
    val q = TestQueue(Vector(Vector.range(0, total)))
    val go = AtomicBoolean(false)
    val pool = Executors.newFixedThreadPool(threads)
    val leased =
      try
        val jobs: Seq[Callable[Vector[Int]]] = (0 until threads).map: _ =>
          new Callable[Vector[Int]]:
            def call(): Vector[Int] =
              while !go.get() do Thread.onSpinWait()
              Iterator.continually(q.lease(0)).takeWhile(_.isDefined).flatten.toVector
        val futures = jobs.map(pool.submit)
        go.set(true)
        futures.toVector.flatMap(_.get())
      finally
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)
        ()
    Result
      .assert(leased.size == total)
      .and(Result.assert(leased.distinct.size == leased.size))
      .and(Result.assert(q.remaining == 0))
      .log(s"threads=$threads leased=${leased.size} distinct=${leased.distinct.size}")

  def exHasWork: Result =
    // What decides whether a worker task forks a JVM at all, so it has to answer for a queue that is
    // drained and for one that still holds units but has stopped serving them.
    val q = TestQueue(Vector(Vector(0), Vector(1)))
    val atStart = q.hasWork
    q.lease(0)
    val partlyDrained = q.hasWork
    q.lease(1)
    val drained = q.hasWork
    val poisoned = TestQueue(Vector(Vector(0, 1)))
    poisoned.poison()
    Result
      .assert(atStart)
      .and(Result.assert(partlyDrained))
      .and(Result.assert(!drained))
      // Units remain, but a poisoned queue will not serve them, so spawning for them is waste.
      .and(Result.assert(poisoned.remaining == 2))
      .and(Result.assert(!poisoned.hasWork))
      .log(s"atStart=$atStart partly=$partlyDrained drained=$drained")

  def exOutOfRange: Result =
    val q = TestQueue(Vector(Vector(0)))
    Result
      .assert(q.lease(-1).isEmpty)
      .and(Result.assert(q.lease(5).isEmpty))
      .and(Result.assert(q.lease(0) == Some(0)))
end TestQueueTest
