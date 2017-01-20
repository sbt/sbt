package sbt
package internal

import java.util.concurrent.atomic.{ AtomicLong, AtomicInteger }
import java.io.Closeable
import sbt.util.{ Logger, LogExchange, Level }
import sbt.internal.util.MainAppender
import Def.ScopedKey
import scala.concurrent.ExecutionContext

/**
 * Interface between sbt and a thing running in the background.
 */
private[sbt] abstract class BackgroundJob {
  def humanReadableName: String
  def awaitTermination(): Unit
  def shutdown(): Unit
  // this should be true on construction and stay true until
  // the job is complete
  def isRunning(): Boolean
  // called after stop or on spontaneous exit, closing the result
  // removes the listener
  def onStop(listener: () => Unit)(implicit ex: ExecutionContext): Closeable
  // do we need this or is the spawning task good enough?
  // def tags: SomeType
}

private[sbt] abstract class AbstractJobHandle extends JobHandle {
  override def toString = s"JobHandle(${id}, ${humanReadableName}, ${Def.showFullKey(spawningTask)})"
}

private[sbt] abstract class AbstractBackgroundJobService extends BackgroundJobService {
  private val nextId = new AtomicLong(1)
  private val pool = new BackgroundThreadPool()

  // hooks for sending start/stop events
  protected def onAddJob(job: JobHandle): Unit = {}
  protected def onRemoveJob(job: JobHandle): Unit = {}

  // this mutable state could conceptually go on State except
  // that then every task that runs a background job would have
  // to be a command, so not sure what to do here.
  @volatile
  private final var jobSet = Set.empty[ThreadJobHandle]
  private def addJob(job: ThreadJobHandle): Unit = synchronized {
    onAddJob(job)
    jobSet += job
  }

  private def removeJob(job: ThreadJobHandle): Unit = synchronized {
    onRemoveJob(job)
    jobSet -= job
  }
  override def jobs: Vector[ThreadJobHandle] = jobSet.toVector

  final class ThreadJobHandle(
      override val id: Long, override val spawningTask: ScopedKey[_],
      val logger: Logger, val job: BackgroundJob
  ) extends AbstractJobHandle {
    def humanReadableName: String = job.humanReadableName
    // EC for onStop handler below
    import ExecutionContext.Implicits.global
    job.onStop { () =>
      // TODO: Fix this
      // logger.close()
      removeJob(this)
    }
    addJob(this)
    override final def equals(other: Any): Boolean = other match {
      case handle: JobHandle if handle.id == id => true
      case _                                    => false
    }
    override final def hashCode(): Int = id.hashCode
  }

  private val unknownTask = TaskKey[Unit]("unknownTask", "Dummy value")
  // we use this if we deserialize a handle for a job that no longer exists
  private final class DeadHandle(override val id: Long, override val humanReadableName: String)
      extends AbstractJobHandle {
    override val spawningTask: ScopedKey[_] = unknownTask
  }

  protected def makeContext(id: Long, spawningTask: ScopedKey[_], state: State): Logger

  def doRunInBackground(spawningTask: ScopedKey[_], state: State, start: (Logger) => BackgroundJob): JobHandle = {
    val id = nextId.getAndIncrement()
    val logger = makeContext(id, spawningTask, state)
    val job = try new ThreadJobHandle(id, spawningTask, logger, start(logger))
    catch {
      case e: Throwable =>
        // TODO: Fix this
        // logger.close()
        throw e
    }
    job
  }

  override def runInBackground(spawningTask: ScopedKey[_], state: State)(start: (Logger) => Unit): JobHandle = {
    pool.run(this, spawningTask, state)(start)
  }

  override def close(): Unit = {
    while (jobSet.nonEmpty) {
      jobSet.headOption.foreach {
        case handle: ThreadJobHandle @unchecked =>
          handle.job.shutdown()
          handle.job.awaitTermination()
        case _ => //
      }
    }
    pool.close()
  }

  private def withHandle(job: JobHandle)(f: ThreadJobHandle => Unit): Unit = job match {
    case handle: ThreadJobHandle @unchecked => f(handle)
    case dead: DeadHandle @unchecked        => () // nothing to stop or wait for
    case other                              => sys.error(s"BackgroundJobHandle does not originate with the current BackgroundJobService: $other")
  }

  override def stop(job: JobHandle): Unit =
    withHandle(job)(_.job.shutdown())

  override def waitFor(job: JobHandle): Unit =
    withHandle(job)(_.job.awaitTermination())

  override def toString(): String = s"BackgroundJobService(jobs=${jobs.map(_.id).mkString})"
}

private[sbt] object BackgroundThreadPool {
  sealed trait Status
  case object Waiting extends Status
  final case class Running(thread: Thread) extends Status
  // the oldThread is None if we never ran
  final case class Stopped(oldThread: Option[Thread]) extends Status
}

private[sbt] class BackgroundThreadPool extends java.io.Closeable {

  private val nextThreadId = new java.util.concurrent.atomic.AtomicInteger(1)
  private val threadGroup = Thread.currentThread.getThreadGroup()

  private val threadFactory = new java.util.concurrent.ThreadFactory() {
    override def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(threadGroup, runnable, s"sbt-bg-threads-${nextThreadId.getAndIncrement}")
      // Do NOT setDaemon because then the code in TaskExit.scala in sbt will insta-kill
      // the backgrounded process, at least for the case of the run task.
      thread
    }
  }

  private val executor = new java.util.concurrent.ThreadPoolExecutor(
    0, /* corePoolSize */
    32, /* maxPoolSize, max # of bg tasks */
    2, java.util.concurrent.TimeUnit.SECONDS, /* keep alive unused threads this long (if corePoolSize < maxPoolSize) */
    new java.util.concurrent.SynchronousQueue[Runnable](),
    threadFactory
  )

  private class BackgroundRunnable(val taskName: String, body: () => Unit)
      extends BackgroundJob with Runnable {
    import BackgroundThreadPool._
    private val finishedLatch = new java.util.concurrent.CountDownLatch(1)
    // synchronize to read/write this, no sync to just read
    @volatile
    private var status: Status = Waiting

    // double-finally for extra paranoia that we will finishedLatch.countDown
    override def run() = try {
      val go = synchronized {
        status match {
          case Waiting =>
            status = Running(Thread.currentThread())
            true
          case Stopped(_) =>
            false
          case Running(_) =>
            throw new RuntimeException("Impossible status of bg thread")
        }
      }
      try { if (go) body() }
      finally cleanup()
    } finally finishedLatch.countDown()

    private class StopListener(val callback: () => Unit, val executionContext: ExecutionContext) extends Closeable {
      override def close(): Unit = removeListener(this)
      override def hashCode: Int = System.identityHashCode(this)
      override def equals(other: Any): Boolean = other match {
        case r: AnyRef => this eq r
        case _         => false
      }
    }

    // access is synchronized
    private var stopListeners = Set.empty[StopListener]

    private def removeListener(listener: StopListener): Unit = synchronized {
      stopListeners -= listener
    }

    def cleanup(): Unit = {
      // avoid holding any lock while invoking callbacks, and
      // handle callbacks being added by other callbacks, just
      // to be all fancy.
      while (synchronized { stopListeners.nonEmpty }) {
        val listeners = synchronized {
          val list = stopListeners.toList
          stopListeners = Set.empty
          list
        }
        listeners.foreach { l =>
          l.executionContext.execute(new Runnable { override def run = l.callback() })
        }
      }
    }

    override def onStop(listener: () => Unit)(implicit ex: ExecutionContext): Closeable =
      synchronized {
        val result = new StopListener(listener, ex)
        stopListeners += result
        result
      }
    override def awaitTermination(): Unit = finishedLatch.await()
    override def humanReadableName: String = taskName
    override def isRunning(): Boolean =
      status match {
        case Waiting               => true // we start as running from BackgroundJob perspective
        case Running(thread)       => thread.isAlive()
        case Stopped(threadOption) => threadOption.map(_.isAlive()).getOrElse(false)
      }
    override def shutdown(): Unit =
      synchronized {
        status match {
          case Waiting =>
            status = Stopped(None) // makes run() not run the body
          case Running(thread) =>
            status = Stopped(Some(thread))
            thread.interrupt()
          case Stopped(threadOption) =>
            // try to interrupt again! woot!
            threadOption.foreach(_.interrupt())
        }
      }
  }

  def run(manager: AbstractBackgroundJobService, spawningTask: ScopedKey[_], state: State)(work: (Logger) => Unit): JobHandle = {
    def start(logger: Logger): BackgroundJob = {
      val runnable = new BackgroundRunnable(spawningTask.key.label, { () =>
        work(logger)
      })
      executor.execute(runnable)
      runnable
    }
    manager.doRunInBackground(spawningTask, state, start _)
  }

  override def close(): Unit = {
    executor.shutdown()
  }
}

private[sbt] class DefaultBackgroundJobService extends AbstractBackgroundJobService {
  private val generateId: AtomicInteger = new AtomicInteger

  override def makeContext(id: Long, spawningTask: ScopedKey[_], state: State): Logger = {
    val extracted = Project.extract(state)
    LogManager.constructBackgroundLog(extracted.structure.data, state)(spawningTask)
  }
}
